package com.cmcu.itstudy.repository.custom;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Custom persistence operations for {@code DocumentPreviewArtifact} that
 * require SQL Server native locking semantics.
 *
 * <p>The fragment is wired into
 * {@link com.cmcu.itstudy.repository.DocumentPreviewArtifactRepository}
 * by Spring Data JPA's standard {@code Impl} suffix resolution.</p>
 *
 * <h2>Concurrency contract</h2>
 * <p>{@link #claim(int, LocalDateTime, LocalDateTime)} uses a single
 * CTE + UPDATE with
 * {@code WITH (UPDLOCK, READPAST, READCOMMITTEDLOCK)} +
 * {@code OUTPUT inserted.*} to atomically transition up to
 * {@code batchSize} claimable rows. The implementation MUST NOT load
 * candidates and update them in separate queries; doing so breaks the
 * worker claim semantics.</p>
 *
 * <h2>RCSI compatibility</h2>
 * <p>{@code READCOMMITTEDLOCK} forces classic (non-snapshot)
 * read-committed semantics so {@code READPAST} continues to skip
 * locked rows even when the database option
 * {@code READ_COMMITTED_SNAPSHOT} is {@code ON}. The
 * {@code READCOMMITTEDLOCK} hint is mutually exclusive with the row
 * granularity hint {@code ROWLOCK} on the same table reference
 * (SQL Server rejects combining two hints from the same
 * granularity group); this contract therefore uses
 * {@code UPDLOCK, READPAST, READCOMMITTEDLOCK} and DOES NOT use
 * {@code ROWLOCK}. The Phase&nbsp;O2 contract adds the hint
 * unconditionally; it does NOT change the database option itself.</p>
 *
 * <h2>State-update contract</h2>
 * <p>{@link #markReady}, {@link #markRetry} and {@link #markDead} are
 * guarded updates that include the
 * {@code attempt_count = :claimedAttemptCount} predicate. They return
 * {@code true} only when exactly one row was updated. A return value
 * of {@code false} means the row has either been re-claimed by a
 * different worker or has lost its ownership token &mdash; the caller
 * MUST treat that as a lost-ownership condition, not as a transient
 * error.</p>
 *
 * <h2>Why no {@code releaseToRetry}</h2>
 * <p>The earlier draft fragment exposed a {@code releaseToRetry}
 * method that was functionally identical to {@link #markRetry}. The
 * Phase&nbsp;O2 correction removes it; callers that need a retry-path
 * release use {@link #markRetry} directly. Keeping two methods with
 * identical semantics risks divergent retry rules in future
 * refactors.</p>
 */
public interface DocumentPreviewArtifactClaimRepository {

    /**
     * Maximum safe batch size enforced by
     * {@link #claim(int, LocalDateTime, LocalDateTime)}. Callers MUST
     * not request a {@code batchSize} larger than this value.
     */
    int MAX_BATCH_SIZE = 50;

    /**
     * Atomically claims up to {@code batchSize} ready preview artifacts
     * in deterministic priority order. Each claimed artifact is flipped
     * to {@code PROCESSING}, has its {@code attempt_count} incremented,
     * and is stamped with {@code claimed_at} inside the same atomic
     * statement.
     *
     * <p>A row is claimable when ALL of the following hold:</p>
     * <ul>
     *   <li>{@code cleanup_task_id IS NULL};</li>
     *   <li>{@code attempt_count < max_attempts};</li>
     *   <li>{@code next_attempt_at <= :now};</li>
     *   <li>{@code status IN ('PENDING', 'RETRY')} OR
     *       {@code status = 'PROCESSING' AND claimed_at < :staleBefore}.</li>
     *   <li>For {@code LIMITED} rows, the corresponding {@code FULL}
     *       row with the same business key
     *       ({@code (document_file_id, source_checksum_sha256,
     *       variant_version)}) is already {@code READY}.</li>
     * </ul>
     *
     * <p>When the claim SQL selects among multiple claimable rows, the
     * effective priority order is:</p>
     * <ol>
     *   <li>{@code FULL} + {@code PENDING} &mdash; highest priority;
     *       brand-new FULL artifacts are claimed first.</li>
     *   <li>Other {@code PENDING} &mdash; LIMITED can land here when
     *       its FULL sibling is READY.</li>
     *   <li>{@code RETRY} &mdash; backoff-due rows claim only after
     *       PENDING is empty.</li>
     *   <li>Stale {@code PROCESSING} &mdash; reclaim path; lowest
     *       priority.</li>
     * </ol>
     *
     * @param batchSize    1..{@value #MAX_BATCH_SIZE}
     * @param now          current timestamp (server-side reference);
     *                     must not be null
     * @param staleBefore  cutoff for reclaiming abandoned
     *                     {@code PROCESSING} rows; must satisfy
     *                     {@code staleBefore <= now} and not be null
     * @return immutable snapshots of the claimed rows, never null
     */
    List<DocumentPreviewArtifactClaim> claim(
            int batchSize,
            LocalDateTime now,
            LocalDateTime staleBefore);

    /**
     * Marks the artifact READY with the provided storage coordinates.
     * Updates only when {@code status = 'PROCESSING'} AND
     * {@code attempt_count = :claimedAttemptCount}. Returns true iff
     * exactly one row was updated.
     */
    boolean markReady(
            UUID artifactId,
            int claimedAttemptCount,
            String storageBucket,
            String storagePath,
            int totalPages,
            LocalDateTime now);

    /**
     * Requeues the artifact for retry, but ONLY when the row has
     * remaining attempt budget ({@code attempt_count < max_attempts}).
     * Updates only when {@code status = 'PROCESSING'} AND
     * {@code attempt_count = :claimedAttemptCount}. Returns true iff
     * exactly one row was updated.
     */
    boolean markRetry(
            UUID artifactId,
            int claimedAttemptCount,
            LocalDateTime nextAttemptAt,
            String lastError,
            LocalDateTime now);

    /**
     * Marks the artifact terminal DEAD. Updates only when
     * {@code status = 'PROCESSING'} AND
     * {@code attempt_count = :claimedAttemptCount}. Returns true iff
     * exactly one row was updated.
     */
    boolean markDead(
            UUID artifactId,
            int claimedAttemptCount,
            String lastError,
            LocalDateTime now);
}