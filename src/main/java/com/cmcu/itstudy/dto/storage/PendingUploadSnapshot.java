package com.cmcu.itstudy.dto.storage;

import com.cmcu.itstudy.enums.PendingUploadStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable snapshot of a {@code PendingStorageUpload} row, taken inside a
 * short read-only transaction by {@link
 * com.cmcu.itstudy.service.contract.PendingUploadSnapshotService}.
 *
 * <p>The snapshot is a value object: it does NOT keep a reference to a
 * managed JPA entity. The orchestrator passes the snapshot around outside
 * any database transaction (including across a remote Supabase HTTP call),
 * so leaking a managed entity would risk detached-entity writes once the
 * transaction is closed.
 *
 * <p>The {@link #status()} field lets callers distinguish a still-bindable
 * snapshot ({@link PendingUploadStatus#PENDING}) from one that has already
 * been moved by a concurrent request. The {@link #expiresAt()} field is
 * the authoritative deadline copied from the database at load time; it is
 * never recomputed by callers.
 */
public final class PendingUploadSnapshot {

    private final UUID uploadId;
    private final UUID userId;
    private final String storageBucket;
    private final String storagePath;
    private final String expectedFileName;
    private final String expectedMimeType;
    private final Long expectedSizeBytes;
    private final PendingUploadStatus status;
    private final LocalDateTime expiresAt;

    public PendingUploadSnapshot(
            UUID uploadId,
            UUID userId,
            String storageBucket,
            String storagePath,
            String expectedFileName,
            String expectedMimeType,
            Long expectedSizeBytes,
            PendingUploadStatus status,
            LocalDateTime expiresAt) {

        if (uploadId == null) {
            throw new IllegalArgumentException("uploadId must not be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (storageBucket == null || storageBucket.isBlank()) {
            throw new IllegalArgumentException("storageBucket must not be blank");
        }
        if (storagePath == null || storagePath.isBlank()) {
            throw new IllegalArgumentException("storagePath must not be blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("expiresAt must not be null");
        }

        this.uploadId = uploadId;
        this.userId = userId;
        this.storageBucket = storageBucket;
        this.storagePath = storagePath;
        this.expectedFileName = expectedFileName;
        this.expectedMimeType = expectedMimeType;
        this.expectedSizeBytes = expectedSizeBytes;
        this.status = status;
        this.expiresAt = expiresAt;
    }

    public UUID uploadId() {
        return uploadId;
    }

    public UUID userId() {
        return userId;
    }

    public String storageBucket() {
        return storageBucket;
    }

    public String storagePath() {
        return storagePath;
    }

    public String expectedFileName() {
        return expectedFileName;
    }

    public String expectedMimeType() {
        return expectedMimeType;
    }

    public Long expectedSizeBytes() {
        return expectedSizeBytes;
    }

    public PendingUploadStatus status() {
        return status;
    }

    public LocalDateTime expiresAt() {
        return expiresAt;
    }

    /**
     * Returns a new snapshot with {@link #status()} replaced. Used when the
     * caller needs to reflect the post-update status (e.g. an idempotent
     * re-check after the failure service marked the row CANCELED).
     */
    public PendingUploadSnapshot withStatus(PendingUploadStatus newStatus) {
        return new PendingUploadSnapshot(
                uploadId, userId, storageBucket, storagePath,
                expectedFileName, expectedMimeType, expectedSizeBytes,
                newStatus, expiresAt);
    }
}