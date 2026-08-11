package com.cmcu.itstudy.repository.custom.impl;

import com.cmcu.itstudy.enums.PendingUploadStatus;
import com.cmcu.itstudy.repository.custom.PendingStorageUploadClaimRepository;
import com.cmcu.itstudy.repository.custom.PendingUploadTransitionTarget;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * SQL Server implementation of {@link PendingStorageUploadClaimRepository}.
 *
 * <p>Concurrency primitives:
 * <ul>
 *   <li>UPDLOCK + READPAST + ROWLOCK for claim queries (skip locked rows).</li>
 *   <li>CTE + UPDATE with OUTPUT for ordered atomic batch claim.</li>
 *   <li>TOP (...) bound parameter; no string concatenation of {@code batchSize}.</li>
 * </ul>
 *
 * <p>NOTE: queries are SQL Server only. JPQL cannot express the required
 * locking hints.
 */
public class PendingStorageUploadClaimRepositoryImpl
        implements PendingStorageUploadClaimRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public PendingStorageUploadClaimRepositoryImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<UUID> claimExpiredUploads(LocalDateTime now, int batchSize) {
        if (batchSize <= 0) {
            return List.of();
        }

        final String sql = """
                ;WITH claimed AS (
                    SELECT TOP (:batchSize) *
                    FROM dbo.tbl_pending_storage_uploads
                         WITH (UPDLOCK, READPAST, ROWLOCK)
                    WHERE status = 'PENDING'
                      AND expires_at <= :now
                    ORDER BY expires_at, upload_id
                )
                UPDATE claimed
                SET status = 'CLEANING',
                    updated_at = :now
                OUTPUT inserted.upload_id;
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("batchSize", batchSize)
                .addValue("now", now);

        return jdbc.queryForList(sql, params, UUID.class);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean bindPendingUpload(
            UUID uploadId,
            UUID userId,
            UUID documentId,
            LocalDateTime now) {

        final String sql = """
                UPDATE dbo.tbl_pending_storage_uploads
                SET status = 'BOUND',
                    bound_document_id = :documentId,
                    updated_at = :now
                WHERE upload_id = :uploadId
                  AND user_id = :userId
                  AND status = 'PENDING'
                  AND expires_at > :now
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("uploadId", uploadId)
                .addValue("userId", userId)
                .addValue("documentId", documentId)
                .addValue("now", now);

        int updated = jdbc.update(sql, params);
        return updated == 1;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean markPendingUploadCleaning(
            UUID uploadId,
            UUID userId,
            LocalDateTime now) {

        final String sql = """
                UPDATE dbo.tbl_pending_storage_uploads
                SET status = 'CLEANING',
                    updated_at = :now
                WHERE upload_id = :uploadId
                  AND user_id = :userId
                  AND status = 'PENDING'
                  AND expires_at > :now
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("uploadId", uploadId)
                .addValue("userId", userId)
                .addValue("now", now);

        return jdbc.update(sql, params) == 1;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public int markExpired(UUID uploadId, LocalDateTime now) {
        final String sql = """
                UPDATE dbo.tbl_pending_storage_uploads
                SET status = 'EXPIRED',
                    updated_at = :now
                WHERE upload_id = :uploadId
                  AND status = 'PENDING'
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("uploadId", uploadId)
                .addValue("now", now);

        return jdbc.update(sql, params);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<PendingUploadTransitionTarget>
            cancelPendingForVerificationFailure(
                    UUID uploadId,
                    UUID userId,
                    LocalDateTime now) {

        if (uploadId == null || userId == null || now == null) {
            throw new IllegalArgumentException(
                    "cancelPendingForVerificationFailure requires non-null"
                            + " uploadId, userId, now");
        }

        // Race-safe PENDING -> CANCELED. The status filter is the
        // state-machine predicate: a row that has already been
        // transitioned (BOUND, CANCELED, CLEANING, EXPIRED) is left
        // untouched. The expires_at > :now guard is evaluated
        // INSIDE the same UPDATE so a row that expired concurrently
        // is also left untouched (it will be picked up by the
        // claimExpiredPendingForCleanup path).
        //
        // OUTPUT inserted.* returns the post-update values, which
        // are the authoritative bucket/path at the moment of
        // transition. UPDLOCK + ROWLOCK blocks a concurrent
        // bindPendingUpload / markPendingUploadCleaning for the
        // same row, and the conditional WHERE prevents the load
        // + setStatus + save race we used to have in the failure
        // service.
        final String sql = """
                UPDATE dbo.tbl_pending_storage_uploads
                WITH (UPDLOCK, ROWLOCK)
                SET status = 'CANCELED',
                    updated_at = :now
                OUTPUT
                    inserted.upload_id,
                    inserted.storage_bucket,
                    inserted.storage_path,
                    inserted.status
                WHERE upload_id = :uploadId
                  AND user_id = :userId
                  AND status = 'PENDING'
                  AND expires_at > :now
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("uploadId", uploadId)
                .addValue("userId", userId)
                .addValue("now", now);

        return queryForTransitionTarget(sql, params,
                PendingUploadStatus.CANCELED);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<PendingUploadTransitionTarget>
            claimExpiredPendingForCleanup(
                    UUID uploadId,
                    UUID userId,
                    LocalDateTime now) {

        if (uploadId == null || userId == null || now == null) {
            throw new IllegalArgumentException(
                    "claimExpiredPendingForCleanup requires non-null"
                            + " uploadId, userId, now");
        }

        // Race-safe PENDING -> CLEANING for a SINGLE row whose
        // expires_at <= :now. Same shape as the cancel query but
        // the expires_at predicate is reversed. The status filter
        // is the state-machine predicate; UPDLOCK + ROWLOCK keeps
        // a concurrent bind / cancel from sneaking in.
        final String sql = """
                UPDATE dbo.tbl_pending_storage_uploads
                WITH (UPDLOCK, ROWLOCK)
                SET status = 'CLEANING',
                    updated_at = :now
                OUTPUT
                    inserted.upload_id,
                    inserted.storage_bucket,
                    inserted.storage_path,
                    inserted.status
                WHERE upload_id = :uploadId
                  AND user_id = :userId
                  AND status = 'PENDING'
                  AND expires_at <= :now
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("uploadId", uploadId)
                .addValue("userId", userId)
                .addValue("now", now);

        return queryForTransitionTarget(sql, params,
                PendingUploadStatus.CLEANING);
    }

    /**
     * Internal helper that runs a transition UPDATE and maps the
     * {@code OUTPUT inserted.*} projection to a
     * {@link PendingUploadTransitionTarget}.
     *
     * <p>An empty {@link Optional} is returned when the UPDATE
     * matched zero rows. Any other exception is propagated so the
     * surrounding {@code REQUIRES_NEW} transaction in
     * {@link com.cmcu.itstudy.service.impl.PendingUploadFailureServiceImpl}
     * rolls back.
     */
    private Optional<PendingUploadTransitionTarget> queryForTransitionTarget(
            String sql,
            MapSqlParameterSource params,
            PendingUploadStatus expectedStatus) {

        RowMapper<PendingUploadTransitionTarget> mapper = (rs, rowNum) -> {
            UUID id = rs.getObject("upload_id", UUID.class);
            String bucket = rs.getString("storage_bucket");
            String path = rs.getString("storage_path");
            PendingUploadStatus status = PendingUploadStatus.valueOf(
                    rs.getString("status"));
            // The repository only ever transitions to the expected
            // status. If the database returns a different value, the
            // schema has drifted; fail fast.
            if (status != expectedStatus) {
                throw new IllegalStateException(
                        "Atomic transition returned unexpected status: "
                                + status + " (expected " + expectedStatus
                                + ")");
            }
            return switch (expectedStatus) {
                case CANCELED -> PendingUploadTransitionTarget.canceled(
                        id, bucket, path);
                case CLEANING -> PendingUploadTransitionTarget.cleaning(
                        id, bucket, path);
                default -> throw new IllegalStateException(
                        "Unsupported transition status: " + expectedStatus);
            };
        };

        try {
            PendingUploadTransitionTarget target =
                    jdbc.queryForObject(sql, params, mapper);
            return Optional.ofNullable(target);
        } catch (EmptyResultDataAccessException noRow) {
            return Optional.empty();
        }
    }
}