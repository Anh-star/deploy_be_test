package com.cmcu.itstudy.repository.custom;

import com.cmcu.itstudy.entity.StorageCleanupTask;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Custom JPA repository operations for {@link StorageCleanupTask}.
 * Backed by SQL Server native queries via NamedParameterJdbcTemplate.
 *
 * <p>Concurrency is controlled by UPDLOCK + READPAST + ROWLOCK on a CTE-based
 * UPDATE that returns at most {@code batchSize} rows in deterministic order
 * (next_retry_at, id).
 */
public interface StorageCleanupTaskClaimRepository {

    /**
     * Atomically claims up to {@code batchSize} ready cleanup tasks in
     * oldest-first order. Each claimed task is flipped to IN_PROGRESS,
     * has attempt_count incremented, and is stamped with claimed_at.
     *
     * @param now        current timestamp from caller
     * @param batchSize  must be greater than 0
     * @return list of claimed tasks, ordered by next_retry_at asc, id asc
     */
    List<StorageCleanupTask> claimReadyTasks(LocalDateTime now, int batchSize);

    /**
     * Marks the given task as DONE with optional last-error reason.
     * Returns true iff exactly one row was updated.
     */
    boolean markDone(Long taskId, String lastErrorOrNull);

    /**
     * Requeues a task for retry up to maxAttempts. Returns true on success.
     * Caller is responsible for picking nextRetryAt.
     */
    boolean markRetry(Long taskId, LocalDateTime nextRetryAt, String lastError);

    /**
     * Marks the task terminal DEAD. Returns true on success.
     */
    boolean markDead(Long taskId, String lastError);

    /**
     * Recovers stale IN_PROGRESS tasks that were abandoned by a crashed
     * worker. Returns the total number of rows updated.
     */
    int recoverStaleInProgress(LocalDateTime staleBefore, String safeLastError);
}