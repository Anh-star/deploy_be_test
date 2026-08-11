package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.config.SupabaseProperties;
import com.cmcu.itstudy.dto.document.DocumentCardDto;
import com.cmcu.itstudy.dto.document.DocumentCreateRequestDto;
import com.cmcu.itstudy.dto.storage.PendingUploadSnapshot;
import com.cmcu.itstudy.dto.storage.StorageObjectInfo;
import com.cmcu.itstudy.entity.User;
import com.cmcu.itstudy.enums.PendingUploadStatus;
import com.cmcu.itstudy.enums.StorageCleanupReason;
import com.cmcu.itstudy.handle.PendingUploadExpiredException;
import com.cmcu.itstudy.handle.PrivateBucketNotConfiguredException;
import com.cmcu.itstudy.handle.StorageObjectMimeMismatchException;
import com.cmcu.itstudy.handle.StorageObjectNotFoundException;
import com.cmcu.itstudy.handle.StorageObjectSizeMismatchException;
import com.cmcu.itstudy.service.contract.PaidDocumentUploadOrchestrator;
import com.cmcu.itstudy.service.contract.PendingUploadFailureService;
import com.cmcu.itstudy.service.contract.PendingUploadSnapshotService;
import com.cmcu.itstudy.service.contract.SupabaseStorageService;
import com.cmcu.itstudy.service.contract.TransactionalPaidDocumentBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Default implementation of {@link PaidDocumentUploadOrchestrator}.
 *
 * <h2>Transaction boundary</h2>
 * <p>The orchestrator is annotated {@code @Transactional(NOT_SUPPORTED)}.
 * Every step is either inside a tiny own-transaction helper exposed by
 * an external bean (no self-invocation) or runs outside any database
 * transaction. Specifically:
 * <ul>
 *   <li>Step 1 (preliminary verify) is delegated to
 *       {@link PendingUploadSnapshotService#loadOwnedPendingSnapshot}
 *       which opens a fresh {@code REQUIRES_NEW, readOnly = true}
 *       transaction and returns an immutable snapshot.</li>
 *   <li>Step 2 (Supabase object info) NEVER runs inside a DB
 *       transaction.</li>
 *   <li>Step 3 (mark pending CANCELED + enqueue cleanup on size / MIME
 *       mismatch) is delegated to
 *       {@link PendingUploadFailureService#cancelAndEnqueueVerificationFailure}
 *       which opens its own {@code REQUIRES_NEW} transaction; the row
 *       transition and the cleanup-task INSERT commit atomically.</li>
 *   <li>Step 3b (expired pending) is delegated to
 *       {@link PendingUploadFailureService#transitionExpiredAndEnqueueCleanup}
 *       which atomically moves PENDING → CLEANING and enqueues an
 *       {@code EXPIRED_PENDING_UPLOAD} cleanup task.</li>
 *   <li>Step 4 (binder) opens its own {@code REQUIRED} transaction
 *       inside {@link TransactionalPaidDocumentBinder}.</li>
 * </ul>
 *
 * <h2>No self-invocation</h2>
 * <p>The orchestrator used to keep both the preliminary verify and the
 * cancel-and-cleanup as {@code @Transactional} methods on the same
 * class, which Spring silently bypassed. After the C1 correction, every
 * transactional call leaves the orchestrator through an injected bean so
 * the proxy is honoured.
 *
 * <h2>Single source of "now"</h2>
 * <p>The orchestrator computes {@code now} from the application clock so
 * the bind deadline / cleanup scheduling do not depend on JVM default
 * zone drift between beans.
 *
 * <h2>Failure semantics</h2>
 * <p>The orchestrator is the only place that distinguishes between
 * "the client never uploaded" (object missing → leave pending PENDING)
 * and "the client uploaded garbage" (size / MIME mismatch → mark
 * pending CANCELED and enqueue BIND_FAIL_NEW). The remote Supabase
 * object is NOT deleted by this orchestrator; the cleanup scheduler
 * (later phase) picks the task up.
 */
@Service
public class PaidDocumentUploadOrchestratorImpl implements PaidDocumentUploadOrchestrator {

    private static final Logger log =
            LoggerFactory.getLogger(PaidDocumentUploadOrchestratorImpl.class);

    private final SupabaseProperties supabaseProperties;
    private final SupabaseStorageService supabaseStorageService;
    private final TransactionalPaidDocumentBinder binder;
    private final PendingUploadSnapshotService pendingUploadSnapshotService;
    private final PendingUploadFailureService pendingUploadFailureService;
    private final Clock clock;

    public PaidDocumentUploadOrchestratorImpl(
            SupabaseProperties supabaseProperties,
            SupabaseStorageService supabaseStorageService,
            TransactionalPaidDocumentBinder binder,
            PendingUploadSnapshotService pendingUploadSnapshotService,
            PendingUploadFailureService pendingUploadFailureService,
            Clock clock) {
        this.supabaseProperties = supabaseProperties;
        this.supabaseStorageService = supabaseStorageService;
        this.binder = binder;
        this.pendingUploadSnapshotService = pendingUploadSnapshotService;
        this.pendingUploadFailureService = pendingUploadFailureService;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DocumentCardDto orchestratePaidCreate(
            DocumentCreateRequestDto metadata,
            User currentUser) {

        if (metadata == null) {
            throw new IllegalArgumentException("metadata must not be null");
        }
        if (currentUser == null || currentUser.getId() == null) {
            throw new IllegalArgumentException("currentUser must not be null");
        }
        if (!Boolean.TRUE.equals(metadata.getIsPaid())) {
            throw new IllegalArgumentException(
                    "orchestratePaidCreate must only be called for paid documents");
        }
        UUID uploadId = metadata.getUploadId();
        if (uploadId == null) {
            // Defense-in-depth — the DTO cross-field rule already
            // rejects null uploadIds for PAID.
            throw new IllegalArgumentException("uploadId is required for paid create");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        UUID currentUserId = currentUser.getId();

        // 1. Preliminary verify of the pending snapshot. Runs inside a
        //    tiny REQUIRES_NEW readOnly transaction exposed by an
        //    external bean (no self-invocation). The transaction commits
        //    BEFORE the Supabase HTTP call.
        PendingUploadSnapshot snapshot;
        try {
            snapshot = pendingUploadSnapshotService.loadOwnedPendingSnapshot(
                    uploadId, currentUserId, now);
        } catch (PendingUploadExpiredException expired) {
            // C1 expired path: atomic PENDING → CLEANING + enqueue
            // EXPIRED_PENDING_UPLOAD. The transition and the task commit
            // together or not at all.
            pendingUploadFailureService.transitionExpiredAndEnqueueCleanup(
                    uploadId, currentUserId, now, "expired_in_precheck");
            throw expired;
        }

        String privateBucket = supabaseProperties.getPrivateDocumentBucket();
        if (privateBucket == null || privateBucket.isBlank()) {
            throw new PrivateBucketNotConfiguredException(
                    "Private document bucket is not configured");
        }

        // 2. Call Supabase object-info. This MUST run outside any DB
        //    transaction; the @Transactional(NOT_SUPPORTED) annotation
        //    on this method enforces that for the duration of step 2.
        StorageObjectInfo verified;
        try {
            verified = supabaseStorageService.getObjectInfo(
                    snapshot.storageBucket(), snapshot.storagePath());
        } catch (StorageObjectNotFoundException e) {
            // Leave pending PENDING — the user may retry until expiry.
            log.info("Paid-create object missing on Supabase; leaving pending PENDING");
            throw e;
        } catch (RuntimeException e) {
            // 401 / 403 / 5xx / network — surface as a safe storage
            // error; do NOT cancel the pending row (the upload may
            // succeed once Supabase recovers).
            log.warn("Paid-create object-info failed; leaving pending PENDING");
            throw e;
        }

        // 3. Verify actual values against expected values.
        if (!Objects.equals(verified.sizeBytes(), snapshot.expectedSizeBytes())) {
            pendingUploadFailureService.cancelAndEnqueueVerificationFailure(
                    uploadId, currentUserId,
                    StorageCleanupReason.BIND_FAIL_NEW,
                    now,
                    "size_mismatch");
            throw new StorageObjectSizeMismatchException(
                    "Uploaded object size does not match declared size");
        }
        if (snapshot.expectedMimeType() == null
                || verified.contentType() == null
                || !snapshot.expectedMimeType().equalsIgnoreCase(
                        verified.contentType())) {
            pendingUploadFailureService.cancelAndEnqueueVerificationFailure(
                    uploadId, currentUserId,
                    StorageCleanupReason.BIND_FAIL_NEW,
                    now,
                    "mime_mismatch");
            throw new StorageObjectMimeMismatchException(
                    "Uploaded object content type does not match declared MIME type");
        }

        // Snapshot carries the latest status the loader saw. If the row
        // has already moved (someone else won the race), let the binder
        // surface the conflict — the snapshot is informational only.
        if (snapshot.status() != PendingUploadStatus.PENDING) {
            log.warn(
                    "Pending upload {} snapshot already in status {}; binder will resolve",
                    uploadId, snapshot.status());
        }

        // 4. Delegate to the binder. The binder opens its own
        //    transaction; no remote Supabase call happens inside it.
        return binder.bindPaidCreate(metadata, uploadId, currentUserId, verified, now);
    }
}