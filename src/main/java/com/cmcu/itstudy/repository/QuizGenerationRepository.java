package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.QuizGeneration;
import com.cmcu.itstudy.enums.QuizGenerationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 2B / 2C / 2D persistence for {@link QuizGeneration}.
 *
 * <p>Only JPQL-derived methods are exposed — no native SQL.</p>
 */
public interface QuizGenerationRepository
        extends JpaRepository<QuizGeneration, UUID> {

    /**
     * Returns every {@link QuizGeneration} row attached to the supplied
     * document, ordered newest-first.
     *
     * <p>Phase Multi Auto Quiz 1 dropped the
     * {@code uq_quiz_generation_document} unique constraint so a
     * document can carry N rows. Callers that historically relied on
     * "at most one row" semantics should grab index 0 of this list
     * (the latest requested generation).</p>
     *
     * <p>Ordering is deterministic:
     * {@code requestedAt DESC, createdAt DESC, id DESC} so two rows
     * requested within the same millisecond still have a stable
     * relative order.</p>
     */
    @Query("""
            SELECT q
              FROM QuizGeneration q
             WHERE q.document.id = :documentId
             ORDER BY q.requestedAt DESC, q.createdAt DESC, q.id DESC
            """)
    List<QuizGeneration> findAllByDocument_IdOrderByRequestedAtDesc(
            @Param("documentId") UUID documentId);

    Optional<QuizGeneration> findByQuiz_Id(UUID quizId);

    List<QuizGeneration> findAllByQuiz_IdIn(Collection<UUID> quizIds);

    /**
     * Phase 2C — atomic WAITING_SOURCE → QUEUED transition.
     *
     * <p>Updates the {@code QuizGeneration} rows attached to
     * {@code documentId} <em>and</em> anchored to {@code documentFileId}
     * <em>and</em> currently in {@link QuizGenerationStatus#WAITING_SOURCE}
     * to {@link QuizGenerationStatus#QUEUED}, stamping {@code updated_at}
     * with {@code now}.</p>
     *
     * <p>Phase Multi Auto Quiz 1: a document may now carry multiple
     * {@code QuizGeneration} rows (the unique constraint was dropped),
     * so this UPDATE can transition {@code N} rows in a single
     * statement — that is the intended multi-generation behaviour for
     * DOC / DOCX sources that become READY once.</p>
     *
     * <p>Returns the number of rows affected:</p>
     * <ul>
     *   <li>{@code N ≥ 1} — the transition succeeded for N rows.</li>
     *   <li>{@code 0} — no row matched: either no generation exists for
     *       {@code documentId}, the generation is anchored to a different
     *       {@code documentFileId}, or the status is not
     *       {@code WAITING_SOURCE} (idempotent no-op).</li>
     * </ul>
     *
     * <p>This is a single {@code UPDATE} statement: there is no SELECT
     * between read and write, no entity materialisation, no lazy-loading
     * proxy, no dirty-tracking race. The atomicity is enforced by the
     * database engine.</p>
     *
     * <p>Safety: the {@code WHERE} clause guarantees that the following
     * statuses are never overwritten:</p>
     * <ul>
     *   <li>{@code QUEUED}, {@code PROCESSING}, {@code READY},
     *       {@code FAILED}, {@code CANCELLED} — all preserved.</li>
     *   <li>A {@code CANCELLED} row is therefore guaranteed to never
     *       resurrect, even if a late READY signal arrives after the
     *       document has been soft-deleted.</li>
     * </ul>
     *
     * <p>The update does NOT touch any of the following columns
     * (they are preserved automatically because they are absent from
     * the {@code SET} clause):</p>
     * <ul>
     *   <li>{@code requested_question_count}</li>
     *   <li>{@code attempts}</li>
     *   <li>{@code requested_at}</li>
     *   <li>{@code processing_at}, {@code ready_at}, {@code failed_at},
     *       {@code cancelled_at}</li>
     *   <li>{@code last_error}, {@code last_attempt_at},
     *       {@code next_attempt_at}, {@code dispatch_token}, etc.</li>
     * </ul>
     *
     * @param documentId     the document whose generation rows should be
     *                       promoted
     * @param documentFileId the primary file that is now READY; must match
     *                       each generation's {@code document_file_id}
     * @param status         the only status that allows the transition
     *                       (always {@link QuizGenerationStatus#WAITING_SOURCE}
     *                       — the parameter exists so JPQL binding works)
     * @param now            caller-supplied timestamp for {@code updated_at}
     * @return number of rows affected (0 to {@code N})
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE QuizGeneration q
               SET q.status = com.cmcu.itstudy.enums.QuizGenerationStatus.QUEUED,
                   q.updatedAt = :now
             WHERE q.document.id = :documentId
               AND q.documentFile.id = :documentFileId
               AND q.status = :status
            """)
    int promoteWaitingSourceToQueued(
            @Param("documentId") UUID documentId,
            @Param("documentFileId") UUID documentFileId,
            @Param("status") QuizGenerationStatus status,
            @Param("now") LocalDateTime now);

    /**
     * Phase 2D — fetch candidate {@code QUEUED} generations that are
     * ready to be claimed by the dispatcher.
     *
     * <p>A candidate row is one that:</p>
     * <ul>
     *   <li>is in {@link QuizGenerationStatus#QUEUED};</li>
     *   <li>has NO outstanding {@code dispatchToken} lease — i.e. the
     *       previous lease either timed out (no claim matched in
     *       time) or was never issued;</li>
     *   <li>has either no {@code next_attempt_at} (fresh) or a
     *       {@code next_attempt_at} that is due now or earlier.</li>
     * </ul>
     *
     * <p>This query returns up to {@code limit} rows ordered by
     * {@code requested_at} ascending so the oldest generations are
     * dispatched first. Read-only — the row is NOT transitioned; the
     * dispatcher still owns the lease via
     * {@link #claimQueuedForDispatch(UUID, UUID, UUID, LocalDateTime)}.</p>
     *
     * @param limit maximum number of candidate rows
     * @param now   current cycle time
     * @return list of candidate generation ids (0 to {@code limit})
     */
    @Query("""
            SELECT q.id
              FROM QuizGeneration q
             WHERE q.status = com.cmcu.itstudy.enums.QuizGenerationStatus.QUEUED
               AND q.dispatchToken IS NULL
               AND (q.nextAttemptAt IS NULL OR q.nextAttemptAt <= :now)
             ORDER BY q.requestedAt ASC
            """)
    List<UUID> findQueuedDispatchCandidates(
            @Param("limit") int limit,
            @Param("now") LocalDateTime now);

    /**
     * Phase 2D — atomic claim of a {@code QUEUED} generation.
     *
     * <p>Transitions the row from {@code QUEUED} to {@code QUEUED}
     * (the status is intentionally preserved so the row remains
     * queue-eligible for re-attempt on HTTP failure) AND stamps a
     * fresh {@code dispatchToken} lease that:</p>
     * <ul>
     *   <li>matches {@code :dispatchToken};</li>
     *   <li>has {@code dispatchTokenIssuedAt = :now};</li>
     *   <li>clears {@code lastError} so a fresh lease is never
     *       associated with a stale error.</li>
     * </ul>
     *
     * <p>The {@code WHERE} clause requires:</p>
     * <ul>
     *   <li>{@code id = :generationId};</li>
     *   <li>status = {@code QUEUED};</li>
     *   <li>{@code dispatchToken IS NULL} — i.e. no other worker
     *       already holds the lease.</li>
     * </ul>
     *
     * <p>Returns the number of rows affected:</p>
     * <ul>
     *   <li>{@code 1} — claim succeeded.</li>
     *   <li>{@code 0} — claim lost: another worker already leased
     *       the row, or the row is no longer in {@code QUEUED}
     *       (e.g. CANCELLED during dispatch).</li>
     * </ul>
     *
     * <p>The transaction commits BEFORE the HTTP call, so the
     * lease is durable across JVM crashes. Concurrent workers
     * competing for the same row will see exactly one
     * {@code affectedRows == 1} response.</p>
     */
    @Modifying
    @Query("""
            UPDATE QuizGeneration q
               SET q.dispatchToken = :dispatchToken,
                   q.dispatchTokenIssuedAt = :now,
                   q.lastError = NULL,
                   q.updatedAt = :now
             WHERE q.id = :generationId
               AND q.status = com.cmcu.itstudy.enums.QuizGenerationStatus.QUEUED
               AND q.dispatchToken IS NULL
            """)
    int claimQueuedForDispatch(
            @Param("generationId") UUID generationId,
            @Param("dispatchToken") UUID dispatchToken,
            @Param("now") LocalDateTime now);

    /**
     * Phase 2D — atomic QUEUED + dispatchToken → PROCESSING.
     *
     * <p>Called after n8n has returned an HTTP 2xx. Updates exactly
     * the row whose {@code id} matches AND whose current
     * {@code dispatchToken} matches the lease the dispatcher holds.
     * When the row has been CANCELLED (or its lease has been
     * rotated) the match fails and {@code affectedRows == 0}; the
     * dispatcher then drops the response without resurrecting a
     * CANCELLED row.</p>
     *
     * <p>Side effects of the transition:</p>
     * <ul>
     *   <li>{@code status = PROCESSING}</li>
     *   <li>{@code processing_at = :now}</li>
     *   <li>{@code last_attempt_at = :now}</li>
     *   <li>{@code attempts = attempts + 1}</li>
     *   <li>{@code updated_at = :now}</li>
     *   <li>{@code next_attempt_at = NULL} — the row no longer
     *       re-enters the queue</li>
     *   <li>{@code last_error = NULL} — clear any prior transient
     *       error code</li>
     *   <li>{@code dispatchToken} PRESERVED — the future
     *       secure-source / callback work will use the same token
     *       to correlate.</li>
     * </ul>
     *
     * <p>The update does NOT touch:</p>
     * <ul>
     *   <li>{@code requested_question_count}</li>
     *   <li>{@code requested_at}, {@code ready_at}, {@code failed_at},
     *       {@code cancelled_at}</li>
     * </ul>
     *
     * @param generationId id of the row to transition
     * @param dispatchToken the lease token the dispatcher holds
     * @param now          current cycle time
     * @return 1 on success, 0 on CANCELLED race or stale lease
     */
    @Modifying
    @Query("""
            UPDATE QuizGeneration q
               SET q.status = com.cmcu.itstudy.enums.QuizGenerationStatus.PROCESSING,
                   q.processingAt = :now,
                   q.lastAttemptAt = :now,
                   q.attempts = q.attempts + 1,
                   q.updatedAt = :now,
                   q.nextAttemptAt = NULL,
                   q.lastError = NULL
             WHERE q.id = :generationId
               AND q.status = com.cmcu.itstudy.enums.QuizGenerationStatus.QUEUED
               AND q.dispatchToken = :dispatchToken
            """)
    int markDispatchedToProcessing(
            @Param("generationId") UUID generationId,
            @Param("dispatchToken") UUID dispatchToken,
            @Param("now") LocalDateTime now);

    /**
     * Phase 2D — HTTP failure → retry.
     *
     * <p>Transitions the row back to {@code QUEUED} for the next
     * cycle, recording the failure on the existing
     * {@code dispatchToken} lease (which is intentionally cleared
     * so the next cycle can re-claim the row from scratch).</p>
     *
     * <p>Side effects:</p>
     * <ul>
     *   <li>{@code status = QUEUED} (no-op already; kept explicit
     *       for the {@code WHERE}-match intent)</li>
     *   <li>{@code last_attempt_at = :now}</li>
     *   <li>{@code attempts = attempts + 1}</li>
     *   <li>{@code last_error = :lastError} — bounded operational
     *       code, NEVER the raw HTTP body</li>
     *   <li>{@code next_attempt_at = :nextAttemptAt}</li>
     *   <li>{@code dispatchToken = NULL} — clear the lease so the
     *       next cycle can re-claim</li>
     *   <li>{@code dispatchTokenIssuedAt = NULL} — clear the lease
     *       timestamp</li>
     *   <li>{@code updated_at = :now}</li>
     * </ul>
     *
     * <p>The {@code WHERE} clause requires:</p>
     * <ul>
     *   <li>{@code id = :generationId}</li>
     *   <li>{@code status = QUEUED} (the row is still eligible —
     *       a CANCELLED race prevents resurrection)</li>
     *   <li>{@code dispatchToken = :dispatchToken} — only the
     *       worker that holds the lease can release it</li>
     * </ul>
     *
     * @param generationId id of the row to retry
     * @param dispatchToken the lease token the dispatcher holds
     * @param lastError bounded operational code (already sanitised)
     * @param nextAttemptAt when the next cycle may reclaim this row
     * @param now          current cycle time
     * @return 1 on success, 0 on CANCELLED race or stale lease
     */
    @Modifying
    @Query("""
            UPDATE QuizGeneration q
               SET q.status = com.cmcu.itstudy.enums.QuizGenerationStatus.QUEUED,
                   q.lastAttemptAt = :now,
                   q.attempts = q.attempts + 1,
                   q.lastError = :lastError,
                   q.nextAttemptAt = :nextAttemptAt,
                   q.dispatchToken = NULL,
                   q.dispatchTokenIssuedAt = NULL,
                   q.updatedAt = :now
             WHERE q.id = :generationId
               AND q.status = com.cmcu.itstudy.enums.QuizGenerationStatus.QUEUED
               AND q.dispatchToken = :dispatchToken
            """)
    int releaseLeaseForRetry(
            @Param("generationId") UUID generationId,
            @Param("dispatchToken") UUID dispatchToken,
            @Param("lastError") String lastError,
            @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
            @Param("now") LocalDateTime now);

    /**
     * Phase 2D — HTTP failure after max attempts → terminal FAILED.
     *
     * <p>Same WHERE-clause as
     * {@link #releaseLeaseForRetry} but the SET clause transitions
     * the row to {@code FAILED} instead of leaving it in
     * {@code QUEUED}.</p>
     *
     * <p>Side effects:</p>
     * <ul>
     *   <li>{@code status = FAILED}</li>
     *   <li>{@code failed_at = :now}</li>
     *   <li>{@code last_attempt_at = :now}</li>
     *   <li>{@code attempts = attempts + 1}</li>
     *   <li>{@code last_error = :lastError}</li>
     *   <li>{@code next_attempt_at = NULL}</li>
     *   <li>{@code dispatchToken = NULL}</li>
     *   <li>{@code dispatchTokenIssuedAt = NULL}</li>
     *   <li>{@code updated_at = :now}</li>
     * </ul>
     *
     * @param generationId id of the row to fail
     * @param dispatchToken the lease token the dispatcher holds
     * @param lastError bounded operational code (already sanitised)
     * @param now          current cycle time
     * @return 1 on success, 0 on CANCELLED race or stale lease
     */
    @Modifying
    @Query("""
            UPDATE QuizGeneration q
               SET q.status = com.cmcu.itstudy.enums.QuizGenerationStatus.FAILED,
                   q.failedAt = :now,
                   q.lastAttemptAt = :now,
                   q.attempts = q.attempts + 1,
                   q.lastError = :lastError,
                   q.nextAttemptAt = NULL,
                   q.dispatchToken = NULL,
                   q.dispatchTokenIssuedAt = NULL,
                   q.updatedAt = :now
             WHERE q.id = :generationId
               AND q.status = com.cmcu.itstudy.enums.QuizGenerationStatus.QUEUED
               AND q.dispatchToken = :dispatchToken
            """)
    int releaseLeaseToFailed(
            @Param("generationId") UUID generationId,
            @Param("dispatchToken") UUID dispatchToken,
            @Param("lastError") String lastError,
            @Param("now") LocalDateTime now);

    /**
     * Phase 2D — crash-recovery path. Releases stale dispatch
     * leases so a future cycle can re-claim the row.
     *
     * <p>A lease is stale when:</p>
     * <ul>
     *   <li>{@code status = QUEUED} (the row never transitioned to
     *       PROCESSING / FAILED / CANCELLED);</li>
     *   <li>{@code dispatchToken IS NOT NULL} (a previous cycle
     *       committed the lease but never wrote the outcome);</li>
     *   <li>{@code dispatchTokenIssuedAt < :staleBefore} OR
     *       {@code dispatchTokenIssuedAt IS NULL} (defensive:
     *       orphan tokens from corrupted or historical rows are
     *       treated as stale so they cannot remain forever).</li>
     * </ul>
     *
     * <p>The update clears ONLY the lease and stamps
     * {@code updated_at}:</p>
     * <ul>
     *   <li>{@code dispatchToken = NULL}</li>
     *   <li>{@code dispatchTokenIssuedAt = NULL}</li>
     *   <li>{@code updated_at = :now}</li>
     * </ul>
     *
     * <p>The update does NOT touch:</p>
     * <ul>
     *   <li>{@code status} — left as {@code QUEUED} so the row
     *       immediately re-enters the candidate list;</li>
     *   <li>{@code attempts} — the JVM crashed BEFORE the HTTP
     *       outcome was known, so no attempt has actually
     *       occurred;</li>
     *   <li>{@code lastAttemptAt}, {@code nextAttemptAt},
     *       {@code lastError} — these reflect the previous
     *       attempt's outcome and MUST NOT be rewritten;</li>
     *   <li>{@code processingAt}, {@code failedAt} — the row never
     *       transitioned out of {@code QUEUED};</li>
     *   <li>{@code requestedAt}, {@code requestedQuestionCount},
     *       {@code document}, {@code documentFile}.</li>
     * </ul>
     *
     * <p>The {@code WHERE} clause guarantees that the following
     * statuses are NEVER altered by this recovery:</p>
     * <ul>
     *   <li>{@code WAITING_SOURCE} (no lease ever issued);</li>
     *   <li>{@code PROCESSING} (an in-flight HTTP call is still
     *       being awaited);</li>
     *   <li>{@code READY} / {@code FAILED} (terminal — the lease
     *       is no longer relevant);</li>
     *   <li>{@code CANCELLED} (terminal — MUST NOT be touched).</li>
     * </ul>
     *
     * <p>Returns the number of rows released.</p>
     *
     * @param staleBefore the cutoff — rows whose
     *                    {@code dispatchTokenIssuedAt < staleBefore}
     *                    (or whose {@code dispatchTokenIssuedAt}
     *                    IS NULL) are released
     * @param now         caller-supplied timestamp for {@code updated_at}
     * @return number of stale leases released (0 to {@code N})
     */
    @Modifying
    @Query("""
            UPDATE QuizGeneration q
               SET q.dispatchToken = NULL,
                   q.dispatchTokenIssuedAt = NULL,
                   q.updatedAt = :now
             WHERE q.status = com.cmcu.itstudy.enums.QuizGenerationStatus.QUEUED
               AND q.dispatchToken IS NOT NULL
               AND (q.dispatchTokenIssuedAt IS NULL
                    OR q.dispatchTokenIssuedAt < :staleBefore)
            """)
    int releaseStaleDispatchLeases(
            @Param("staleBefore") LocalDateTime staleBefore,
            @Param("now") LocalDateTime now);
}