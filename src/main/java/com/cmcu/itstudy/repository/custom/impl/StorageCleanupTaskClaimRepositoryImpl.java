package com.cmcu.itstudy.repository.custom.impl;

import com.cmcu.itstudy.entity.StorageCleanupTask;
import com.cmcu.itstudy.enums.StorageCleanupTaskStatus;
import com.cmcu.itstudy.repository.custom.StorageCleanupTaskClaimRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * SQL Server implementation of {@link StorageCleanupTaskClaimRepository}.
 * Uses CTE + TOP + UPDLOCK + READPAST + ROWLOCK for safe worker claim
 * semantics.
 */
public class StorageCleanupTaskClaimRepositoryImpl
        implements StorageCleanupTaskClaimRepository {

    private static final RowMapper<StorageCleanupTask> TASK_ROW_MAPPER =
            (rs, rowNum) -> mapTask(rs);

    private final NamedParameterJdbcTemplate jdbc;

    public StorageCleanupTaskClaimRepositoryImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<StorageCleanupTask> claimReadyTasks(LocalDateTime now, int batchSize) {
        if (batchSize <= 0) {
            return List.of();
        }

        final String sql = """
                ;WITH claimed AS (
                    SELECT TOP (:batchSize) *
                    FROM dbo.tbl_storage_cleanup_tasks
                         WITH (UPDLOCK, READPAST, ROWLOCK)
                    WHERE status IN ('PENDING', 'RETRY')
                      AND next_retry_at <= :now
                      AND attempt_count < max_attempts
                    ORDER BY next_retry_at, id
                )
                UPDATE claimed
                SET status = 'IN_PROGRESS',
                    attempt_count = attempt_count + 1,
                    claimed_at = :now,
                    updated_at = :now
                OUTPUT
                    inserted.id,
                    inserted.target_bucket,
                    inserted.target_path,
                    inserted.reason,
                    inserted.pending_upload_id,
                    inserted.document_id,
                    inserted.attempt_count,
                    inserted.max_attempts;
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("batchSize", batchSize)
                .addValue("now", now);

        return jdbc.query(sql, params, TASK_ROW_MAPPER);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markDone(Long taskId, String lastErrorOrNull) {
        final String sql = """
                UPDATE dbo.tbl_storage_cleanup_tasks
                SET status = 'DONE',
                    claimed_at = NULL,
                    last_error = :lastError
                WHERE id = :id
                  AND status = 'IN_PROGRESS'
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", taskId)
                .addValue("lastError", lastErrorOrNull);

        return jdbc.update(sql, params) == 1;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markRetry(Long taskId, LocalDateTime nextRetryAt, String lastError) {
        final String sql = """
                UPDATE dbo.tbl_storage_cleanup_tasks
                SET status = 'RETRY',
                    next_retry_at = :nextRetryAt,
                    claimed_at = NULL,
                    last_error = :lastError
                WHERE id = :id
                  AND status = 'IN_PROGRESS'
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", taskId)
                .addValue("nextRetryAt", nextRetryAt)
                .addValue("lastError", lastError);

        return jdbc.update(sql, params) == 1;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markDead(Long taskId, String lastError) {
        final String sql = """
                UPDATE dbo.tbl_storage_cleanup_tasks
                SET status = 'DEAD',
                    claimed_at = NULL,
                    last_error = :lastError
                WHERE id = :id
                  AND status IN ('IN_PROGRESS', 'RETRY', 'PENDING')
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", taskId)
                .addValue("lastError", lastError);

        return jdbc.update(sql, params) == 1;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recoverStaleInProgress(LocalDateTime staleBefore, String safeLastError) {
        // A: Tasks still within retry budget → requeue as RETRY.
        final String retrySql = """
                UPDATE dbo.tbl_storage_cleanup_tasks
                SET status = 'RETRY',
                    claimed_at = NULL,
                    next_retry_at = :now,
                    updated_at = :now,
                    last_error = :lastError
                WHERE status = 'IN_PROGRESS'
                  AND claimed_at < :staleBefore
                  AND attempt_count < max_attempts
                """;

        MapSqlParameterSource retryParams = new MapSqlParameterSource()
                .addValue("now", LocalDateTime.now())
                .addValue("staleBefore", staleBefore)
                .addValue("lastError", safeLastError);

        int retryCount = jdbc.update(retrySql, retryParams);

        // B: Tasks that exhausted retries → mark terminal DEAD.
        final String deadSql = """
                UPDATE dbo.tbl_storage_cleanup_tasks
                SET status = 'DEAD',
                    claimed_at = NULL,
                    updated_at = :now,
                    last_error = :lastError
                WHERE status = 'IN_PROGRESS'
                  AND claimed_at < :staleBefore
                  AND attempt_count >= max_attempts
                """;

        MapSqlParameterSource deadParams = new MapSqlParameterSource()
                .addValue("now", LocalDateTime.now())
                .addValue("staleBefore", staleBefore)
                .addValue("lastError", safeLastError);

        int deadCount = jdbc.update(deadSql, deadParams);

        return retryCount + deadCount;
    }

    private static StorageCleanupTask mapTask(ResultSet rs) throws SQLException {
        StorageCleanupTask t = new StorageCleanupTask();
        t.setId(rs.getLong("id"));
        t.setTargetBucket(rs.getString("target_bucket"));
        t.setTargetPath(rs.getString("target_path"));
        t.setReason(com.cmcu.itstudy.enums.StorageCleanupReason
                .valueOf(rs.getString("reason")));
        // The claim SQL only OUTPUTs columns that the worker needs to act on.
        // Status was just transitioned to IN_PROGRESS by the UPDATE; the worker
        // does not need it from the OUTPUT.
        t.setStatus(StorageCleanupTaskStatus.IN_PROGRESS);
        t.setAttemptCount(rs.getInt("attempt_count"));
        t.setMaxAttempts(rs.getInt("max_attempts"));
        // next_retry_at and claimed_at are updated server-side; the worker
        // only needs them if it explicitly re-reads.
        t.setNextRetryAt(null);
        t.setClaimedAt(null);
        // pendingUpload / document are not eagerly materialized here;
        // callers needing them should load via the entity manager afterwards.
        t.setLastError(null);
        t.setCreatedAt(null);
        t.setUpdatedAt(null);
        return t;
    }
}