package com.cmcu.itstudy.repository.custom;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Custom JPA repository operations for {@link com.cmcu.itstudy.entity.PendingStorageUpload}.
 * Backed by SQL Server native queries via NamedParameterJdbcTemplate.
 *
 * <p>SQL Server locking primitives (UPDLOCK, READPAST, ROWLOCK) are required
 * because the regular Spring Data finder semantics are not race-safe for
 * worker-style concurrent claim operations.
 */
public interface PendingStorageUploadClaimRepository {

    /**
     * Atomically claims a batch of expired pending uploads.
     * Implementation must use TOP + UPDLOCK + READPAST + ROWLOCK and OUTPUT.
     *
     * @param now        current timestamp from caller
     * @param batchSize  maximum number of uploads to claim in one call
     * @return list of upload IDs claimed in this batch (size 0..batchSize)
     */
    List<UUID> claimExpiredUploads(LocalDateTime now, int batchSize);

    /**
     * Atomically binds a PENDING upload to a document.
     * Returns true iff exactly one row was updated.
     * Implementation must run inside its own transaction.
     */
    boolean bindPendingUpload(
            UUID uploadId,
            UUID userId,
            UUID documentId,
            LocalDateTime now
    );

    /**
     * Marks a PENDING upload as CLEANING before object deletion begins.
     * Returns true iff the upload is owned by the given user and was PENDING.
     */
    boolean markPendingUploadCleaning(
            UUID uploadId,
            UUID userId,
            LocalDateTime now
    );

    /**
     * Marks the upload with the given id as EXPIRED.
     * Returns the number of rows updated.
     */
    int markExpired(UUID uploadId, LocalDateTime now);

    /**
     * Race-safe verification-failure transition.
     *
     * <p>Atomically moves a single pending row from {@code PENDING} to
     * {@code CANCELED} IFF:
     * <ul>
     *   <li>{@code upload_id = :uploadId}</li>
     *   <li>{@code user_id = :userId}</li>
     *   <li>{@code status = 'PENDING'}</li>
     *   <li>{@code expires_at > :now}</li>
     * </ul>
     *
     * <p>Implementation MUST run as a single SQL Server
     * {@code UPDATE ... WITH (UPDLOCK, ROWLOCK) ... OUTPUT inserted.*}
     * statement. The returned {@link PendingUploadTransitionTarget} (when
     * present) carries the bucket/path that were visible to the
     * database engine at the exact moment the row was transitioned;
     * they are the authoritative target for the cleanup task.
     *
     * <p>Returns {@link Optional#empty()} when no row matched — either
     * because the row is already bound, canceled, cleaning or expired,
     * or because it has already passed its expiry. Callers MUST treat
     * an empty result as a no-op (the row is in a different lifecycle
     * state and must not be overwritten).
     *
     * @param uploadId  primary key of the pending upload
     * @param userId    owner id (must match the row's {@code user_id})
     * @param now       current timestamp used to evaluate the
     *                  {@code expires_at > :now} predicate
     * @return the authoritative transition target, or empty if no
     *         eligible row existed
     */
    Optional<PendingUploadTransitionTarget> cancelPendingForVerificationFailure(
            UUID uploadId,
            UUID userId,
            LocalDateTime now
    );

    /**
     * Race-safe expiry-claim transition.
     *
     * <p>Atomically moves a single pending row from {@code PENDING} to
     * {@code CLEANING} IFF:
     * <ul>
     *   <li>{@code upload_id = :uploadId}</li>
     *   <li>{@code user_id = :userId}</li>
     *   <li>{@code status = 'PENDING'}</li>
     *   <li>{@code expires_at <= :now}</li>
     * </ul>
     *
     * <p>Implementation MUST run as a single SQL Server
     * {@code UPDATE ... WITH (UPDLOCK, ROWLOCK) ... OUTPUT inserted.*}
     * statement. The returned {@link PendingUploadTransitionTarget} (when
     * present) carries the bucket/path that were visible to the
     * database engine at the exact moment the row was transitioned;
     * they are the authoritative target for the cleanup task.
     *
     * <p>Returns {@link Optional#empty()} when no row matched — either
     * because the row is still bindable, already bound, or already in
     * a terminal state.
     *
     * @param uploadId  primary key of the pending upload
     * @param userId    owner id (must match the row's {@code user_id})
     * @param now       current timestamp used to evaluate the
     *                  {@code expires_at <= :now} predicate
     * @return the authoritative transition target, or empty if no
     *         eligible row existed
     */
    Optional<PendingUploadTransitionTarget> claimExpiredPendingForCleanup(
            UUID uploadId,
            UUID userId,
            LocalDateTime now
    );
}