package com.cmcu.itstudy.repository.custom.impl;

import com.cmcu.itstudy.enums.StorageCleanupReason;
import com.cmcu.itstudy.repository.custom.StorageCleanupTaskInsertRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * SQL Server implementation of {@link StorageCleanupTaskInsertRepository}.
 *
 * <p>Uses an INSERT ... SELECT ... WHERE NOT EXISTS pattern guarded by
 * {@code WITH (UPDLOCK, HOLDLOCK)} on the existence-check subquery. This
 * provides best-effort serialized insert behavior so two concurrent
 * enqueues do not both see the empty slot and both insert.
 *
 * <p>The single HoBt/Page scoped lock is held until the transaction
 * commits, serializing the existence check and the insertion.
 *
 * <p>Authoritative dedup still requires the filtered unique index to be
 * created manually by the operator (out-of-band). This implementation is
 * best-effort while the index is missing.
 */
public class StorageCleanupTaskInsertRepositoryImpl
        implements StorageCleanupTaskInsertRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public StorageCleanupTaskInsertRepositoryImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Long> insertActiveTaskIfAbsent(
            String targetBucket,
            String targetPath,
            StorageCleanupReason reason,
            UUID pendingUploadId,
            UUID documentId,
            LocalDateTime nextRetryAt,
            LocalDateTime now) {

        final String sql = """
                INSERT INTO dbo.tbl_storage_cleanup_tasks (
                    target_bucket,
                    target_path,
                    reason,
                    status,
                    attempt_count,
                    max_attempts,
                    next_retry_at,
                    claimed_at,
                    pending_upload_id,
                    document_id,
                    last_error,
                    created_at,
                    updated_at
                )
                OUTPUT inserted.id
                SELECT
                    :targetBucket,
                    :targetPath,
                    :reason,
                    'PENDING',
                    0,
                    5,
                    :nextRetryAt,
                    NULL,
                    :pendingUploadId,
                    :documentId,
                    NULL,
                    :now,
                    :now
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM dbo.tbl_storage_cleanup_tasks
                         WITH (UPDLOCK, HOLDLOCK)
                    WHERE target_bucket = :targetBucket
                      AND target_path = :targetPath
                      AND reason = :reason
                      AND status IN (
                          'PENDING',
                          'IN_PROGRESS',
                          'RETRY'
                      )
                );
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("targetBucket", targetBucket)
                .addValue("targetPath", targetPath)
                .addValue("reason", reason.name())
                .addValue("pendingUploadId", pendingUploadId)
                .addValue("documentId", documentId)
                .addValue("nextRetryAt", nextRetryAt)
                .addValue("now", now);

        var ids = jdbc.queryForList(sql, params, Long.class);
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(ids.get(0));
    }
}