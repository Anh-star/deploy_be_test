package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.config.SupabaseProperties;
import com.cmcu.itstudy.dto.storage.PaidUploadTargetRequestDto;
import com.cmcu.itstudy.dto.storage.PaidUploadTargetResponseDto;
import com.cmcu.itstudy.dto.storage.SignedUploadTarget;
import com.cmcu.itstudy.entity.PendingStorageUpload;
import com.cmcu.itstudy.entity.User;
import com.cmcu.itstudy.enums.AllowedDocumentFileType;
import com.cmcu.itstudy.handle.PrivateBucketNotConfiguredException;
import com.cmcu.itstudy.handle.SignedUploadTargetFailedException;
import com.cmcu.itstudy.service.contract.PaidUploadFileValidatorService;
import com.cmcu.itstudy.service.contract.PaidUploadTargetOrchestrator;
import com.cmcu.itstudy.service.contract.PendingUploadRegistrationService;
import com.cmcu.itstudy.service.contract.StorageObjectPathService;
import com.cmcu.itstudy.service.contract.SupabaseStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Default implementation of {@link PaidUploadTargetOrchestrator}.
 *
 * <h2>Single source of identity</h2>
 * <p>The orchestrator is the unique source of:
 * <ul>
 *   <li>{@code uploadId} — generated exactly once via {@link UUID#randomUUID()}
 *       and reused for the Supabase path, the persisted row, and the
 *       response.</li>
 *   <li>{@code bindExpiresAt} — the StudyIT pending-upload bind deadline
 *       computed once from {@link Clock#instant()} and reused for the
 *       persisted row and the response. This is NOT the Supabase
 *       signed-token TTL.</li>
 * </ul>
 *
 * <h2>Transaction boundary</h2>
 * <p>The orchestrator is annotated {@code @Transactional(propagation =
 * NOT_SUPPORTED)}: it explicitly forbids running inside any database
 * transaction. Inside it we issue the Supabase HTTP call (which must NOT
 * be transactional) and then call the registration service with its own
 * short transaction ({@code REQUIRED}).
 *
 * <h2>Failure semantics</h2>
 * <ul>
 *   <li>If Supabase fails: do NOT insert {@link PendingStorageUpload};
 *       throw {@link SignedUploadTargetFailedException}.</li>
 *   <li>If the DB insert fails: do NOT return a token to the frontend;
 *       throw a wrapped exception.</li>
 *   <li>The signed token is NEVER logged.</li>
 * </ul>
 */
@Service
public class PaidUploadTargetOrchestratorImpl implements PaidUploadTargetOrchestrator {

    /** Minutes added to "now" to form the StudyIT pending-upload bind deadline. */
    public static final long BIND_DEADLINE_MINUTES = 15L;

    private final SupabaseProperties supabaseProperties;
    private final PaidUploadFileValidatorService validator;
    private final StorageObjectPathService pathService;
    private final SupabaseStorageService supabaseStorageService;
    private final PendingUploadRegistrationService registrationService;
    private final Clock clock;

    public PaidUploadTargetOrchestratorImpl(
            SupabaseProperties supabaseProperties,
            PaidUploadFileValidatorService validator,
            StorageObjectPathService pathService,
            SupabaseStorageService supabaseStorageService,
            PendingUploadRegistrationService registrationService,
            Clock clock) {
        this.supabaseProperties = supabaseProperties;
        this.validator = validator;
        this.pathService = pathService;
        this.supabaseStorageService = supabaseStorageService;
        this.registrationService = registrationService;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaidUploadTargetResponseDto createTarget(
            User currentUser, PaidUploadTargetRequestDto request) {

        // 1. Validate request — side-effect free.
        AllowedDocumentFileType fileType = validator.validate(
                request.getFileName(), request.getMimeType(), request.getSizeBytes());

        if (currentUser == null || currentUser.getId() == null) {
            throw new SignedUploadTargetFailedException("User is not authenticated");
        }

        UUID userId = currentUser.getId();
        // 2. Single, server-generated uploadId. Reused for the path, the
        //    persisted row, and the response.
        UUID uploadId = UUID.randomUUID();

        // 3. Single "now" used for the bind deadline. Both the persisted
        //    row and the response use this exact value.
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime bindExpiresAt = now.plusMinutes(BIND_DEADLINE_MINUTES);

        // 4. Build server-side path.
        String path = pathService.buildPaidUploadPath(
                userId, uploadId, fileType.extension());

        // 5. Resolve private bucket.
        String bucket = supabaseProperties.getPrivateDocumentBucket();
        if (bucket == null || bucket.isBlank()) {
            throw new PrivateBucketNotConfiguredException(
                    "Private document bucket is not configured");
        }

        // 6. Remote Supabase call — must NOT be inside a DB transaction.
        SignedUploadTarget target = supabaseStorageService.createSignedUploadTarget(
                bucket, path);
        if (target == null || target.token() == null || target.token().isBlank()) {
            throw new SignedUploadTargetFailedException(
                    "Supabase target not returned");
        }

        // 7. Register the pending upload. This short transaction lives in
        //    PendingUploadRegistrationService (REQUIRED). The orchestrator
        //    supplies the uploadId, the "now" timestamp, and
        //    bindExpiresAt; the registration service is NOT allowed to
        //    generate any of them internally.
        PendingStorageUpload saved = registrationService.register(
                uploadId,
                currentUser,
                bucket,
                path,
                fileType,
                request.getFileName(),
                request.getSizeBytes(),
                now,
                bindExpiresAt);

        // 8. Defensive: ensure the saved entity carries the exact uploadId
        //    and bindExpiresAt we generated. Fail safely without leaking
        //    the token.
        if (saved.getUploadId() == null || !saved.getUploadId().equals(uploadId)) {
            throw new SignedUploadTargetFailedException(
                    "Pending upload id mismatch after registration");
        }
        if (saved.getExpiresAt() == null || !saved.getExpiresAt().equals(bindExpiresAt)) {
            throw new SignedUploadTargetFailedException(
                    "Bind deadline mismatch after registration");
        }

        // 9. Return response. The token is short-lived and must NEVER be
        //    logged by the caller.
        return PaidUploadTargetResponseDto.builder()
                .uploadId(uploadId)
                .bucket(bucket)
                .path(path)
                .token(target.token())
                .expiresAt(bindExpiresAt)
                .build();
    }
}
