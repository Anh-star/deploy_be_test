package com.cmcu.itstudy.repository.custom;

import com.cmcu.itstudy.enums.PendingUploadStatus;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable carrier returned by race-safe atomic transition UPDATEs in
 * {@link PendingStorageUploadClaimRepository}.
 *
 * <p>The bucket and path fields are the AUTHORITATIVE values returned by
 * the SQL Server {@code OUTPUT inserted} clause of the same statement
 * that flipped the status. They are the values that were visible to
 * the database engine at the exact moment the row was transitioned
 * (under {@code UPDLOCK, ROWLOCK}); no other transaction can change
 * them between the {@code OUTPUT} read and the caller's commit.
 *
 * <p>This type deliberately does NOT expose the full
 * {@link com.cmcu.itstudy.entity.PendingStorageUpload} entity. The
 * transition is a state-machine event, not a managed-entity mutation,
 * and the failure path only needs the three pieces of information
 * required to enqueue a cleanup task (uploadId + bucket + path) plus
 * the resulting status for logging.
 *
 * <p>Instances are constructed only inside the implementation of the
 * repository fragment; callers receive them via
 * {@link java.util.Optional}. Two factory constructors are provided:
 * one for the cancel path (always CANCELED) and one for the expired
 * path (always CLEANING). No public all-args constructor exists so
 * the {@code newStatus} field is constrained to the two values the
 * repository actually transitions into.
 */
public final class PendingUploadTransitionTarget {

    private final UUID uploadId;
    private final String storageBucket;
    private final String storagePath;
    private final PendingUploadStatus newStatus;

    private PendingUploadTransitionTarget(
            UUID uploadId,
            String storageBucket,
            String storagePath,
            PendingUploadStatus newStatus) {
        this.uploadId = Objects.requireNonNull(uploadId, "uploadId");
        this.storageBucket = Objects.requireNonNull(
                storageBucket, "storageBucket");
        this.storagePath = Objects.requireNonNull(
                storagePath, "storagePath");
        this.newStatus = Objects.requireNonNull(newStatus, "newStatus");
    }

    /**
     * Result of a successful {@code PENDING -> CANCELED} transition.
     */
    public static PendingUploadTransitionTarget canceled(
            UUID uploadId,
            String storageBucket,
            String storagePath) {
        return new PendingUploadTransitionTarget(
                uploadId, storageBucket, storagePath,
                PendingUploadStatus.CANCELED);
    }

    /**
     * Result of a successful {@code PENDING -> CLEANING} transition.
     */
    public static PendingUploadTransitionTarget cleaning(
            UUID uploadId,
            String storageBucket,
            String storagePath) {
        return new PendingUploadTransitionTarget(
                uploadId, storageBucket, storagePath,
                PendingUploadStatus.CLEANING);
    }

    public UUID uploadId() {
        return uploadId;
    }

    public String storageBucket() {
        return storageBucket;
    }

    public String storagePath() {
        return storagePath;
    }

    public PendingUploadStatus newStatus() {
        return newStatus;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PendingUploadTransitionTarget that)) return false;
        return uploadId.equals(that.uploadId)
                && storageBucket.equals(that.storageBucket)
                && storagePath.equals(that.storagePath)
                && newStatus == that.newStatus;
    }

    @Override
    public int hashCode() {
        return Objects.hash(uploadId, storageBucket, storagePath, newStatus);
    }

    @Override
    public String toString() {
        return "PendingUploadTransitionTarget{uploadId=" + uploadId
                + ", newStatus=" + newStatus
                + ", storageBucket='" + storageBucket + '\''
                + ", storagePath='" + storagePath + '\''
                + '}';
    }
}
