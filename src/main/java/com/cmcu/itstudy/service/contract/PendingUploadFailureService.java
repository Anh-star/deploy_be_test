package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.enums.PendingUploadStatus;
import com.cmcu.itstudy.enums.StorageCleanupReason;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Atomic state-transition helper for the PAID create flow's failure paths.
 *
 * <p>Exists to break the
 * {@link com.cmcu.itstudy.service.impl.PaidDocumentUploadOrchestratorImpl}
 * self-invocation pattern. The orchestrator's own bean is
 * {@code @Transactional(NOT_SUPPORTED)}; calling any transactional helper
 * inside the same class would silently lose its proxy. By delegating the
 * atomic "mark + enqueue" work to this external bean, the orchestrator
 * guarantees the pending-row transition and the cleanup-task insertion
 * commit (or rollback) as one unit, NEVER as two independent writes.
 *
 * <p>This service is also responsible for the
 * {@link PendingUploadStatus#EXPIRED} path: when a paid create request
 * arrives for a pending row whose {@code expiresAt} is already in the
 * past, the row is atomically moved to {@code CLEANING} and an
 * {@link StorageCleanupReason#EXPIRED_PENDING_UPLOAD} cleanup task is
 * enqueued in the same transaction. A subsequent request that finds the
 * row already in {@code CLEANING} or {@code EXPIRED} gets an idempotent
 * "expired" conflict without creating a duplicate active task.
 */
public interface PendingUploadFailureService {

    /**
     * Outcome of a cancel-and-enqueue attempt, surfaced for testing and
     * for the orchestrator's decision to surface a specific user-facing
     * exception.
     */
    enum Outcome {
        /** Exactly one pending row was moved to CANCELED and one task was inserted. */
        CANCELED,
        /** The pending row is already in a terminal / non-PENDING state (no-op). */
        ALREADY_NOT_PENDING
    }

    /**
     * Outcome of an expire-and-enqueue attempt.
     */
    enum ExpireOutcome {
        /** Exactly one pending row was moved to CLEANING and one task was inserted. */
        CLEANING_SCHEDULED,
        /** No eligible pending row existed (already terminal / EXPIRED / CLEANING). */
        ALREADY_TERMINAL
    }

    /**
     * Atomic PENDING → CANCELED transition plus a {@code BIND_FAIL_NEW}
     * cleanup task. Both writes commit or rollback together.
     *
     * <p>If the pending row is no longer in {@code PENDING} state, this
     * method does nothing and returns {@link Outcome#ALREADY_NOT_PENDING}.
     *
     * @param uploadId           primary key of the pending upload
     * @param currentUserId      authenticated user id (must match
     *                           {@code pending.user_id})
     * @param reason             storage cleanup reason (always
     *                           {@link StorageCleanupReason#BIND_FAIL_NEW}
     *                           for size/MIME mismatch in Phase C1)
     * @param now                single "now" supplied by the orchestrator
     * @param safeFailureCode    short, non-PII diagnostic code for logs
     * @return the outcome of the transition
     */
    Outcome cancelAndEnqueueVerificationFailure(
            UUID uploadId,
            UUID currentUserId,
            StorageCleanupReason reason,
            LocalDateTime now,
            String safeFailureCode);

    /**
     * Atomic PENDING → CLEANING transition plus an
     * {@link StorageCleanupReason#EXPIRED_PENDING_UPLOAD} cleanup task.
     *
     * <p>If the pending row is no longer in {@code PENDING} state (already
     * CLEANING, EXPIRED, BOUND, or CANCELED), this method does nothing
     * and returns {@link ExpireOutcome#ALREADY_TERMINAL}. No duplicate
     * active cleanup task is inserted.
     */
    ExpireOutcome transitionExpiredAndEnqueueCleanup(
            UUID uploadId,
            UUID currentUserId,
            LocalDateTime now,
            String safeFailureCode);
}