package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.entity.StorageCleanupTask;
import com.cmcu.itstudy.enums.StorageCleanupReason;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Persistence-only operations for the storage cleanup task queue.
 *
 * <p>This service does NOT perform any remote storage calls. It only writes
 * to {@code dbo.tbl_storage_cleanup_tasks} so the remote cleanup worker
 * (implemented in a later stage) can pick them up.
 *
 * <p>Idempotency contract: callers may invoke these methods more than once
 * with the same {@code (targetBucket, targetPath, reason)} triple. The
 * filtered unique index on the table (created out-of-band by operator) is
 * the authoritative de-duplication mechanism; service-level duplicate
 * exceptions must be tolerated.
 */
public interface StorageCleanupTaskService {

    /**
     * Enqueue a cleanup for a newly uploaded object whose DB update failed.
     * Bound to an existing pending upload (if any).
     */
    StorageCleanupTask enqueueNewObjectCleanup(
            String targetBucket,
            String targetPath,
            StorageCleanupReason reason,
            UUID pendingUploadId,
            UUID documentId
    );

    /**
     * Enqueue a cleanup for a newly uploaded object inside the CALLER's
     * currently-active transaction.
     *
     * <p>Propagation: {@code MANDATORY}. The implementation refuses to run
     * when no caller transaction is active (the Spring proxy throws
     * {@code IllegalTransactionStateException}). This guarantees the
     * pending-row transition performed by the caller and the cleanup-task
     * insertion performed here commit or rollback as a single atomic
     * unit. There is no second transaction opened from inside the caller.
     *
     * <p>Use this overload for atomic-failure paths (size mismatch,
     * MIME mismatch, EXPIRED transition) where the cleanup task MUST
     * commit with the row transition. Use the
     * {@link #enqueueNewObjectCleanup} overload (REQUIRES_NEW) only when
     * the caller transaction has already rolled back and the task still
     * needs to be recorded.
     */
    StorageCleanupTask enqueueNewObjectCleanupInCurrentTransaction(
            String targetBucket,
            String targetPath,
            StorageCleanupReason reason,
            UUID pendingUploadId,
            UUID documentId
    );

    /**
     * Enqueue a cleanup for an old object whose DB replacement committed but
     * remote deletion still needs to happen.
     */
    StorageCleanupTask enqueueOldObjectCleanup(
            String targetBucket,
            String targetPath,
            StorageCleanupReason reason,
            UUID documentId
    );

    StorageCleanupTask markDone(Long taskId, String lastErrorOrNull);

    StorageCleanupTask markRetry(
            Long taskId,
            LocalDateTime nextRetryAt,
            String lastError);

    StorageCleanupTask markDead(Long taskId, String lastError);

    /**
     * Reclaims stale IN_PROGRESS tasks that have been abandoned by a crashed
     * worker. Returns the number of tasks recovered.
     */
    int recoverStaleInProgress(LocalDateTime staleBefore, String lastError);
}