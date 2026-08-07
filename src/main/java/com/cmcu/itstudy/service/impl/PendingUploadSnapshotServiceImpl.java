package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.config.SupabaseProperties;
import com.cmcu.itstudy.dto.storage.PendingUploadSnapshot;
import com.cmcu.itstudy.entity.PendingStorageUpload;
import com.cmcu.itstudy.entity.User;
import com.cmcu.itstudy.enums.PendingUploadStatus;
import com.cmcu.itstudy.handle.PendingUploadAlreadyBoundException;
import com.cmcu.itstudy.handle.PendingUploadExpiredException;
import com.cmcu.itstudy.handle.PendingUploadNotFoundException;
import com.cmcu.itstudy.handle.PendingUploadNotOwnedException;
import com.cmcu.itstudy.handle.PrivateBucketNotConfiguredException;
import com.cmcu.itstudy.repository.PendingStorageUploadRepository;
import com.cmcu.itstudy.service.contract.PendingUploadSnapshotService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Default implementation of {@link PendingUploadSnapshotService}.
 *
 * <p>The method runs in a fresh {@code REQUIRES_NEW, readOnly = true}
 * transaction so:
 * <ul>
 *   <li>The pending row is read with a real Hibernate session (not a
 *       self-invoked no-op proxy).</li>
 *   <li>The managed {@link PendingStorageUpload} entity is fully
 *       detached at transaction commit. The returned
 *       {@link PendingUploadSnapshot} is a copy, NOT a reference to the
 *       managed entity. The orchestrator may safely pass the snapshot
 *       across the remote Supabase HTTP call without triggering lazy
 *       initialization outside a session, and without risking a detached
 *       write when the transaction is closed.</li>
 *   <li>The {@code REQUIRES_NEW} propagation ensures the read commits
 *       BEFORE the orchestrator's {@code NOT_SUPPORTED} Supabase call,
 *       so the roundtrip is observable on the database side as soon as
 *       the snapshot is built.</li>
 * </ul>
 */
@Service
public class PendingUploadSnapshotServiceImpl implements PendingUploadSnapshotService {

    private final PendingStorageUploadRepository pendingUploadRepository;
    private final SupabaseProperties supabaseProperties;
    private final Clock clock;

    public PendingUploadSnapshotServiceImpl(
            PendingStorageUploadRepository pendingUploadRepository,
            SupabaseProperties supabaseProperties,
            Clock clock) {
        this.pendingUploadRepository = pendingUploadRepository;
        this.supabaseProperties = supabaseProperties;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public PendingUploadSnapshot loadOwnedPendingSnapshot(
            UUID uploadId,
            UUID currentUserId,
            LocalDateTime now) {

        if (uploadId == null) {
            throw new IllegalArgumentException("uploadId must not be null");
        }
        if (currentUserId == null) {
            throw new IllegalArgumentException("currentUserId must not be null");
        }
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }

        // Sample the snapshot's "now" INSIDE the readOnly transaction
        // so the expiry check is evaluated against the time the
        // snapshot is actually taken — not a value the orchestrator
        // captured before entering this method.
        LocalDateTime snapshotNow = LocalDateTime.now(clock);

        PendingStorageUpload pending = pendingUploadRepository.findByUploadId(uploadId)
                .orElseThrow(() -> new PendingUploadNotFoundException(
                        "Pending upload not found"));

        UUID pendingOwnerId = pending.getUser() != null
                ? pending.getUser().getId()
                : null;
        if (pendingOwnerId == null
                || !Objects.equals(pendingOwnerId, currentUserId)) {
            throw new PendingUploadNotOwnedException(
                    "Pending upload does not belong to current user");
        }

        if (pending.getStatus() != PendingUploadStatus.PENDING) {
            throw new PendingUploadAlreadyBoundException(
                    "Pending upload is no longer bindable");
        }

        if (pending.getExpiresAt() == null
                || !pending.getExpiresAt().isAfter(snapshotNow)) {
            throw new PendingUploadExpiredException(
                    "Pending upload bind deadline has passed");
        }

        String privateBucket = supabaseProperties.getPrivateDocumentBucket();
        if (privateBucket == null || privateBucket.isBlank()) {
            throw new PrivateBucketNotConfiguredException(
                    "Private document bucket is not configured");
        }
        if (!Objects.equals(privateBucket, pending.getStorageBucket())) {
            throw new PendingUploadNotOwnedException(
                    "Pending upload does not target configured private bucket");
        }

        String storagePath = pending.getStoragePath();
        if (storagePath == null
                || !storagePath.startsWith("paid/")
                || !storagePath.contains("/" + uploadId + ".")) {
            throw new PendingUploadNotOwnedException(
                    "Pending upload path is not in the server-generated format");
        }

        User user = pending.getUser();
        Long expectedSizeBytes = pending.getExpectedSizeBytes();

        return new PendingUploadSnapshot(
                pending.getUploadId(),
                user != null ? user.getId() : null,
                pending.getStorageBucket(),
                pending.getStoragePath(),
                pending.getExpectedFileName(),
                pending.getExpectedMimeType(),
                expectedSizeBytes,
                pending.getStatus(),
                pending.getExpiresAt());
    }
}