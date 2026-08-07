package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.dto.storage.PendingUploadSnapshot;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Loads an owned, immutable snapshot of a {@code PendingStorageUpload}
 * row for the PAID create flow.
 *
 * <p>This service exists to break the
 * {@link com.cmcu.itstudy.service.impl.PaidDocumentUploadOrchestratorImpl}
 * self-invocation pattern. The orchestrator's own bean is
 * {@code @Transactional(NOT_SUPPORTED)}; any transactional helper inside
 * the same class would silently lose its proxy. By delegating the
 * preliminary read to this external bean, the orchestrator guarantees a
 * real {@code REQUIRES_NEW} read-only transaction commits BEFORE the
 * remote Supabase object-info call, so the orchestrator never carries a
 * managed entity across an HTTP roundtrip.
 *
 * <p>Concrete behaviour:
 * <ul>
 *   <li>{@link #loadOwnedPendingSnapshot(UUID, UUID, LocalDateTime)}
 *       opens a fresh {@code REQUIRES_NEW, readOnly = true} transaction,
 *       loads the pending row by primary key,</li>
 *   <li>verifies ownership, expiry, status, bucket and path shape, and</li>
 *   <li>returns an immutable {@link PendingUploadSnapshot}. The managed
 *       JPA entity never leaves the transaction.</li>
 * </ul>
 */
public interface PendingUploadSnapshotService {

    /**
     * Loads and validates the pending upload owned by {@code currentUserId}.
     *
     * @param uploadId       primary key of {@code tbl_pending_storage_uploads}
     * @param currentUserId  authenticated user id from the controller
     * @param now            single "now" supplied by the orchestrator so all
     *                       time-sensitive checks use the same clock
     * @return immutable snapshot of the pending row, valid at the time of
     *         this call's transaction commit
     * @throws com.cmcu.itstudy.handle.PendingUploadNotFoundException
     *         when no pending row exists for the given uploadId
     * @throws com.cmcu.itstudy.handle.PendingUploadNotOwnedException
     *         when the row is owned by a different user, or its bucket/path
     *         do not match the configured private bucket / server-generated
     *         shape
     * @throws com.cmcu.itstudy.handle.PendingUploadExpiredException
     *         when the row's {@code expiresAt} is not strictly after {@code now}
     * @throws com.cmcu.itstudy.handle.PendingUploadAlreadyBoundException
     *         when the row is no longer in {@code PENDING} state
     * @throws com.cmcu.itstudy.handle.PrivateBucketNotConfiguredException
     *         when no private bucket has been configured on the server
     */
    PendingUploadSnapshot loadOwnedPendingSnapshot(
            UUID uploadId,
            UUID currentUserId,
            LocalDateTime now);
}