package com.cmcu.itstudy.repository.custom.impl;

/**
 * Test-only accessor for the literal claim SQL constant declared
 * inside {@link DocumentPreviewArtifactClaimRepositoryImpl#claim(int,
 * java.time.LocalDateTime, java.time.LocalDateTime)}.
 *
 * <p>The fragment stores its CTE as a {@code final String sql = """ ... """;}
 * block-local constant; surfacing it through a separate package-visible
 * class lets unit tests verify the
 * {@code ORDER BY} / {@code WHERE} clauses without booting a SQL
 * Server instance. The accessor is read-only; nothing else in the
 * codebase depends on it, so a typo here cannot regress production.</p>
 */
public final class DocumentPreviewArtifactClaimRepositoryClaimSql {

    /**
     * The literal claim CTE / UPDATE / OUTPUT block, copied verbatim
     * from {@link DocumentPreviewArtifactClaimRepositoryImpl#claim(int,
     * java.time.LocalDateTime, java.time.LocalDateTime)}. Updated by
     * hand whenever the production SQL changes; the production
     * fragment and this accessor are kept in sync through the
     * latency-optimisation unit tests.
     */
    public static final String CLAIM_SQL = """
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

    private DocumentPreviewArtifactClaimRepositoryClaimSql() {
        // No instances.
    }
}