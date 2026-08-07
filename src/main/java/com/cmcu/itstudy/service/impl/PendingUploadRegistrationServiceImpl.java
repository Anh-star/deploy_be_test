package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.entity.PendingStorageUpload;
import com.cmcu.itstudy.entity.User;
import com.cmcu.itstudy.enums.AllowedDocumentFileType;
import com.cmcu.itstudy.enums.PendingUploadStatus;
import com.cmcu.itstudy.handle.PendingUploadRegistrationFailedException;
import com.cmcu.itstudy.repository.PendingStorageUploadRepository;
import com.cmcu.itstudy.service.contract.PendingUploadRegistrationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Default implementation of {@link PendingUploadRegistrationService}.
 *
 * <p>{@code @Transactional(propagation = REQUIRED)}: the caller (orchestrator)
 * runs the registration inside its own short transaction. Supabase remote
 * calls NEVER participate in this transaction.
 *
 * <p>This implementation:
 * <ul>
 *   <li>never calls {@code UUID.randomUUID()};</li>
 *   <li>never calls {@code LocalDateTime.now()} or
 *       {@code LocalDateTime.now(clock)};</li>
 *   <li>uses the caller-supplied {@code now} for
 *       {@code createdAt}/{@code updatedAt} and the caller-supplied
 *       {@code bindExpiresAt} for {@code expiresAt}.</li>
 * </ul>
 */
@Service
public class PendingUploadRegistrationServiceImpl implements PendingUploadRegistrationService {

    private final PendingStorageUploadRepository pendingUploadRepository;

    public PendingUploadRegistrationServiceImpl(PendingStorageUploadRepository pendingUploadRepository) {
        this.pendingUploadRepository = pendingUploadRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public PendingStorageUpload register(
            UUID uploadId,
            User user,
            String bucket,
            String path,
            AllowedDocumentFileType fileType,
            String fileName,
            long expectedSizeBytes,
            LocalDateTime now,
            LocalDateTime bindExpiresAt) {

        if (uploadId == null) {
            throw new PendingUploadRegistrationFailedException(
                    "uploadId is required (orchestrator must supply it)", null);
        }
        if (now == null) {
            throw new PendingUploadRegistrationFailedException(
                    "now is required (orchestrator must supply it)", null);
        }
        if (bindExpiresAt == null) {
            throw new PendingUploadRegistrationFailedException(
                    "bindExpiresAt is required (orchestrator must supply it)", null);
        }
        try {
            PendingStorageUpload upload = PendingStorageUpload.builder()
                    .uploadId(uploadId)
                    .user(user)
                    .storageBucket(bucket)
                    .storagePath(path)
                    .expectedFileName(fileName)
                    .expectedMimeType(fileType.mimeType())
                    .expectedSizeBytes(expectedSizeBytes)
                    .status(PendingUploadStatus.PENDING)
                    .expiresAt(bindExpiresAt)
                    .boundDocument(null)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            return pendingUploadRepository.save(upload);
        } catch (RuntimeException e) {
            // Wrap so the orchestrator can react without leaking DB internals.
            throw new PendingUploadRegistrationFailedException(
                    "PendingStorageUpload registration failed", e);
        }
    }
}