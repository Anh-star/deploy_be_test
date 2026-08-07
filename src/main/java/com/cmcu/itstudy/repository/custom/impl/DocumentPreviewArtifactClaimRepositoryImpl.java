package com.cmcu.itstudy.repository.custom.impl;

import com.cmcu.itstudy.enums.DocumentPreviewArtifactKind;
import com.cmcu.itstudy.repository.custom.DocumentPreviewArtifactClaim;
import com.cmcu.itstudy.repository.custom.DocumentPreviewArtifactClaimRepository;
import com.cmcu.itstudy.repository.custom.SafeArtifactLastError;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * SQL Server implementation of {@link DocumentPreviewArtifactClaimRepository}.
 *
 * <p>All write methods run in a fresh transaction
 * ({@link Propagation#REQUIRES_NEW}) so the claim path can never be
 * silently merged into a caller's transaction (the worker must perform
 * remote storage I/O OUTSIDE the database transaction).</p>
 *
 * <h2>Why a separate {@code claimable} CTE?</h2>
 * <p>The reference contract uses a CTE that selects the top-N claimable
 * rows under {@code UPDLOCK, READPAST, READCOMMITTEDLOCK} and then
 * JOINs that CTE back to the base table for the UPDATE + OUTPUT. SQL
 * Server cannot {@code UPDATE} a CTE directly when the CTE is the
 * source of the lock and we also need {@code OUTPUT inserted.*}; the
 * JOIN-back-to-base form keeps the SELECT-FROM-CTE semantically clean
 * and avoids implementation-defined behaviour in the OUTPUT
 * projection.</p>
 *
 * <h2>Why {@code READCOMMITTEDLOCK}?</h2>
 * <p>When {@code READ_COMMITTED_SNAPSHOT} is {@code ON} for the
 * database, the standard reader hint {@code READPAST} can be silently
 * ignored because statements operate against a row-version snapshot
 * rather than the live locked row. The {@code READCOMMITTEDLOCK}
 * companion hint forces classic (non-snapshot) read-committed
 * semantics for the referenced table, so {@code READPAST} continues
 * to skip locked rows. {@code READCOMMITTEDLOCK} and {@code ROWLOCK}
 * are mutually exclusive (both belong to the granularity-hint
 * group), so {@code ROWLOCK} is intentionally NOT used. The
 * Phase&nbsp;O2 contract adds {@code READCOMMITTEDLOCK}
 * unconditionally; it does NOT change the database option itself.</p>
 *
 * <h2>Why an explicit {@code ;} prefix?</h2>
 * <p>SQL Server treats the leading semicolon as a defensive terminator
 * against accidentally appending this CTE to a previous statement that
 * did not end with one. The string-concatenation in
 * {@code NamedParameterJdbcTemplate} is consistent with the rest of
 * the project's claim repositories.</p>
 */
public class DocumentPreviewArtifactClaimRepositoryImpl
        implements DocumentPreviewArtifactClaimRepository {

    private static final RowMapper<DocumentPreviewArtifactClaim> CLAIM_ROW_MAPPER =
            (rs, rowNum) -> mapClaim(rs);

    private static final String TABLE =
            "dbo.tbl_document_preview_artifacts";

    private final NamedParameterJdbcTemplate jdbc;

    public DocumentPreviewArtifactClaimRepositoryImpl(
            NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<DocumentPreviewArtifactClaim> claim(
            int batchSize,
            LocalDateTime now,
            LocalDateTime staleBefore) {

        if (batchSize <= 0) {
            throw new IllegalArgumentException(
                    "batchSize must be > 0: " + batchSize);
        }
        if (batchSize > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "batchSize must be <= " + MAX_BATCH_SIZE + ": "
                            + batchSize);
        }
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }
        if (staleBefore == null) {
            throw new IllegalArgumentException(
                    "staleBefore must not be null");
        }
        if (staleBefore.isAfter(now)) {
            throw new IllegalArgumentException(
                    "staleBefore must be <= now; got staleBefore="
                            + staleBefore + ", now=" + now);
        }

        final String sql = """
                ;WITH claimable AS (
                    SELECT TOP (:batchSize) a.id
                    FROM dbo.tbl_document_preview_artifacts a
                         WITH (UPDLOCK, READPAST, READCOMMITTEDLOCK)
                    WHERE
                        a.cleanup_task_id IS NULL
                        AND a.attempt_count < a.max_attempts
                        AND a.next_attempt_at <= :now
                        AND (
                            a.status = N'PENDING'
                            OR a.status = N'RETRY'
                            OR (
                                a.status = N'PROCESSING'
                                AND a.claimed_at < :staleBefore
                            )
                        )
                        -- LIMITED dependency gate: a LIMITED row may
                        -- only be claimed when its FULL sibling with
                        -- the SAME business key (document_file_id,
                        -- source_checksum_sha256, variant_version) is
                        -- already READY. This avoids the
                        -- increment-and-retry pattern that wasted
                        -- attempt budget on LIMITED rows whose FULL
                        -- was still mid-conversion.
                        AND (
                            a.artifact_kind <> N'LIMITED'
                            OR EXISTS (
                                SELECT 1
                                FROM dbo.tbl_document_preview_artifacts f
                                WHERE
                                    f.document_file_id = a.document_file_id
                                    AND f.artifact_kind = N'FULL'
                                    AND f.status = N'READY'
                                    AND f.variant_version = a.variant_version
                                    AND (
                                        (f.source_checksum_sha256 IS NOT NULL
                                         AND a.source_checksum_sha256 IS NOT NULL
                                         AND f.source_checksum_sha256
                                             = a.source_checksum_sha256)
                                        OR (f.source_checksum_sha256 IS NULL
                                            AND a.source_checksum_sha256 IS NULL)
                                    )
                            )
                        )
                    ORDER BY
                        -- Latency priority: claimable FULL PENDING
                        -- wins, then other PENDING, then RETRY, then
                        -- stale PROCESSING. The remaining ORDER BY
                        -- keys preserve fairness inside each bucket.
                        CASE
                            WHEN a.artifact_kind = N'FULL'
                                 AND a.status = N'PENDING' THEN 0
                            WHEN a.status = N'PENDING' THEN 1
                            WHEN a.status = N'RETRY' THEN 2
                            ELSE 3
                        END,
                        a.next_attempt_at,
                        a.created_at,
                        a.id
                )
                UPDATE a
                SET
                    a.status = N'PROCESSING',
                    a.attempt_count = a.attempt_count + 1,
                    a.claimed_at = :now,
                    a.updated_at = :now
                OUTPUT
                    inserted.id,
                    inserted.document_file_id,
                    inserted.artifact_kind,
                    inserted.source_checksum_sha256,
                    inserted.variant_version,
                    inserted.attempt_count,
                    inserted.max_attempts,
                    inserted.claimed_at
                FROM dbo.tbl_document_preview_artifacts a
                JOIN claimable c
                  ON c.id = a.id;
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("batchSize", batchSize)
                .addValue("now", now)
                .addValue("staleBefore", staleBefore);

        return jdbc.query(sql, params, CLAIM_ROW_MAPPER);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markReady(
            UUID artifactId,
            int claimedAttemptCount,
            String storageBucket,
            String storagePath,
            int totalPages,
            LocalDateTime now) {

        // Pre-SQL validation. Because this is a native update, no JPA
        // @PreUpdate callback will fire, so the READY-invariant CHECK
        // constraint is the only safety net for the storage
        // coordinates. Validate them BEFORE the SQL update so the
        // failure mode is deterministic.
        if (artifactId == null) {
            throw new IllegalArgumentException(
                    "artifactId must not be null");
        }
        if (claimedAttemptCount < 1) {
            throw new IllegalArgumentException(
                    "claimedAttemptCount must be >= 1: "
                            + claimedAttemptCount);
        }
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }
        if (storageBucket == null || storageBucket.isBlank()) {
            throw new IllegalArgumentException(
                    "storageBucket must not be blank");
        }
        if (storageBucket.length()
                > com.cmcu.itstudy.entity.DocumentPreviewArtifact
                        .STORAGE_BUCKET_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "storageBucket exceeds max length ("
                            + com.cmcu.itstudy.entity.DocumentPreviewArtifact
                                    .STORAGE_BUCKET_MAX_LENGTH
                            + "): " + storageBucket.length());
        }
        if (storagePath == null || storagePath.isBlank()) {
            throw new IllegalArgumentException(
                    "storagePath must not be blank");
        }
        if (storagePath.length()
                > com.cmcu.itstudy.entity.DocumentPreviewArtifact
                        .STORAGE_PATH_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "storagePath exceeds max length ("
                            + com.cmcu.itstudy.entity.DocumentPreviewArtifact
                                    .STORAGE_PATH_MAX_LENGTH
                            + "): " + storagePath.length());
        }
        if (totalPages <= 0) {
            throw new IllegalArgumentException(
                    "totalPages must be > 0: " + totalPages);
        }

        final String sql = """
                UPDATE dbo.tbl_document_preview_artifacts
                SET
                    status = N'READY',
                    storage_bucket = :storageBucket,
                    storage_path = :storagePath,
                    total_pages = :totalPages,
                    claimed_at = NULL,
                    last_error = NULL,
                    next_attempt_at = :now,
                    updated_at = :now
                WHERE
                    id = :id
                    AND status = N'PROCESSING'
                    AND attempt_count = :claimedAttemptCount;
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", artifactId)
                .addValue("claimedAttemptCount", claimedAttemptCount)
                .addValue("storageBucket", storageBucket)
                .addValue("storagePath", storagePath)
                .addValue("totalPages", totalPages)
                .addValue("now", now);

        return jdbc.update(sql, params) == 1;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markRetry(
            UUID artifactId,
            int claimedAttemptCount,
            LocalDateTime nextAttemptAt,
            String lastError,
            LocalDateTime now) {

        if (artifactId == null) {
            throw new IllegalArgumentException(
                    "artifactId must not be null");
        }
        if (claimedAttemptCount < 1) {
            throw new IllegalArgumentException(
                    "claimedAttemptCount must be >= 1: "
                            + claimedAttemptCount);
        }
        if (nextAttemptAt == null) {
            throw new IllegalArgumentException(
                    "nextAttemptAt must not be null");
        }
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }
        // Strictly greater-than-now: equal-to-now would be reclaimable
        // immediately and breaks the worker back-off contract.
        if (!nextAttemptAt.isAfter(now)) {
            throw new IllegalArgumentException(
                    "nextAttemptAt must be strictly > now; got "
                            + "nextAttemptAt=" + nextAttemptAt
                            + ", now=" + now);
        }
        String sanitisedError =
                SafeArtifactLastError.sanitize(lastError,
                        SafeArtifactLastError.OPERATIONAL_MAX_LENGTH);

        final String sql = """
                UPDATE dbo.tbl_document_preview_artifacts
                SET
                    status = N'RETRY',
                    next_attempt_at = :nextAttemptAt,
                    claimed_at = NULL,
                    last_error = :lastError,
                    updated_at = :now
                WHERE
                    id = :id
                    AND status = N'PROCESSING'
                    AND attempt_count = :claimedAttemptCount
                    AND attempt_count < max_attempts;
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", artifactId)
                .addValue("claimedAttemptCount", claimedAttemptCount)
                .addValue("nextAttemptAt", nextAttemptAt)
                .addValue("lastError", sanitisedError)
                .addValue("now", now);

        return jdbc.update(sql, params) == 1;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markDead(
            UUID artifactId,
            int claimedAttemptCount,
            String lastError,
            LocalDateTime now) {

        if (artifactId == null) {
            throw new IllegalArgumentException(
                    "artifactId must not be null");
        }
        if (claimedAttemptCount < 1) {
            throw new IllegalArgumentException(
                    "claimedAttemptCount must be >= 1: "
                            + claimedAttemptCount);
        }
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }
        String sanitisedError =
                SafeArtifactLastError.sanitize(lastError,
                        SafeArtifactLastError.OPERATIONAL_MAX_LENGTH);

        final String sql = """
                UPDATE dbo.tbl_document_preview_artifacts
                SET
                    status = N'DEAD',
                    claimed_at = NULL,
                    last_error = :lastError,
                    updated_at = :now
                WHERE
                    id = :id
                    AND status = N'PROCESSING'
                    AND attempt_count = :claimedAttemptCount;
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", artifactId)
                .addValue("claimedAttemptCount", claimedAttemptCount)
                .addValue("lastError", sanitisedError)
                .addValue("now", now);

        return jdbc.update(sql, params) == 1;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static DocumentPreviewArtifactClaim mapClaim(ResultSet rs)
            throws SQLException {
        UUID id = readUuid(rs, "id", /* nullable = */ false);
        UUID documentFileId = readUuid(rs, "document_file_id",
                /* nullable = */ false);
        String kindRaw = rs.getString("artifact_kind");
        if (kindRaw == null) {
            throw new SQLException(
                    "artifact_kind is NULL for claimed row id=" + id);
        }
        DocumentPreviewArtifactKind kind =
                DocumentPreviewArtifactKind.valueOf(kindRaw);
        String checksum = rs.getString("source_checksum_sha256");
        int variant = rs.getInt("variant_version");
        int attempt = rs.getInt("attempt_count");
        int max = rs.getInt("max_attempts");
        LocalDateTime claimedAt = rs.getTimestamp("claimed_at") == null
                ? null
                : rs.getTimestamp("claimed_at").toLocalDateTime();
        return new DocumentPreviewArtifactClaim(
                id, documentFileId, kind, checksum,
                variant, attempt, max, claimedAt);
    }

    /**
     * Reads a UUID column from a {@link ResultSet} in a way that is
     * independent of the JDBC driver's type mapping.
     *
     * <p>SQL Server's {@code mssql-jdbc} driver normally returns a
     * {@code uniqueidentifier} column as {@link java.util.UUID}, but
     * some driver configurations (some versions of the driver, or
     * settings like {@code sendStringParametersAsUnicode} that interact
     * badly with the {@code OUTPUT inserted.*} clause) return the same
     * column as a {@link String}. A direct {@code (UUID) rs.getObject(...)}
     * cast therefore throws {@link ClassCastException} at runtime.</p>
     *
     * <p>This helper accepts both runtime shapes:</p>
     * <ul>
     *   <li>JDBC returns {@link UUID} — used directly.</li>
     *   <li>JDBC returns {@link String} — trimmed and parsed with
     *       {@link UUID#fromString(String)}.</li>
     *   <li>JDBC returns {@code null} — returns {@code null} only when
     *       {@code nullable} is {@code true}; otherwise throws
     *       {@link SQLException} identifying the column.</li>
     *   <li>JDBC returns any other type — throws {@link SQLException}
     *       with the column name and runtime type, never echoing the
     *       raw value back into the log message.</li>
     * </ul>
     *
     * <p>The helper never uses {@code Object#toString()} blindly, so an
     * unsupported JDBC type never produces a misleading cast attempt.</p>
     *
     * @param rs the {@link ResultSet} currently positioned on a row
     * @param column column label to read
     * @param nullable whether the column is allowed to be SQL {@code NULL}
     * @return a non-null {@link UUID} when a value is present, or
     *         {@code null} only when {@code nullable} is true and the
     *         actual value is SQL {@code NULL}
     * @throws SQLException when the value is malformed, the wrong type,
     *         or null and the column is non-nullable
     */
    private static UUID readUuid(ResultSet rs, String column, boolean nullable)
            throws SQLException {
        Object raw = rs.getObject(column);
        if (raw == null) {
            if (nullable) {
                return null;
            }
            throw new SQLException(
                    "expected non-null UUID for column " + column
                            + " but got SQL NULL");
        }
        if (raw instanceof UUID uuid) {
            return uuid;
        }
        if (raw instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.isEmpty()) {
                if (nullable) {
                    return null;
                }
                throw new SQLException(
                        "expected non-null UUID for column " + column
                                + " but got empty string");
            }
            try {
                return UUID.fromString(trimmed);
            } catch (IllegalArgumentException bad) {
                throw new SQLException(
                        "expected a UUID string for column " + column
                                + " but got a malformed value of length "
                                + trimmed.length());
            }
        }
        throw new SQLException(
                "expected UUID or String for column " + column
                        + " but got unexpected JDBC type "
                        + raw.getClass().getName());
    }
}