package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.QuizGeneration;
import com.cmcu.itstudy.enums.QuizGenerationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 2B persistence for {@link QuizGeneration}.
 *
 * <p>Only JPQL-derived methods are exposed — no native SQL.</p>
 */
public interface QuizGenerationRepository
        extends JpaRepository<QuizGeneration, UUID> {

    /**
     * Returns the (at-most-one) generation row attached to the supplied
     * document id. The {@code uq_quiz_generation_document} unique
     * constraint guarantees this returns at most one row.
     */
    Optional<QuizGeneration> findByDocument_Id(UUID documentId);

    /**
     * Phase 2C — atomic WAITING_SOURCE → QUEUED transition.
     *
     * <p>Updates exactly the {@code QuizGeneration} row attached to
     * {@code documentId} <em>and</em> anchored to {@code documentFileId}
     * <em>and</em> currently in {@link QuizGenerationStatus#WAITING_SOURCE}
     * to {@link QuizGenerationStatus#QUEUED}, stamping {@code updated_at}
     * with {@code now}.</p>
     *
     * <p>Returns the number of rows affected:</p>
     * <ul>
     *   <li>{@code 1} — the transition succeeded.</li>
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
     * @param documentId     the document whose generation should be promoted
     * @param documentFileId the primary file that is now READY; must match
     *                       the generation's {@code document_file_id}
     * @param status         the only status that allows the transition
     *                       (always {@link QuizGenerationStatus#WAITING_SOURCE}
     *                       — the parameter exists so JPQL binding works)
     * @param now            caller-supplied timestamp for {@code updated_at}
     * @return number of rows affected (0 or 1)
     */
    @Modifying
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
}
