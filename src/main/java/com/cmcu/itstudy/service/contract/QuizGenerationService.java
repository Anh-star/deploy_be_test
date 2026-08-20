package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.entity.QuizGeneration;
import com.cmcu.itstudy.enums.AllowedDocumentFileType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Phase 2B persistence service for AI quiz auto-generation requests.
 *
 * <p>Wired into the document-create transactions (free and paid) so the
 * upload-time intent is captured as a real database row instead of a log
 * line. No n8n call, no signed URL, no scheduler, no callback — those
 * belong to later phases and will read from
 * {@link QuizGeneration#getStatus()}.
 *
 * <p>Phase Multi Auto Quiz 1: a document may carry multiple
 * {@code QuizGeneration} rows. {@code enqueueForDocument} always creates a
 * fresh row (no idempotent reuse of an existing one). Lookups that used
 * to return {@code Optional<QuizGeneration>} now return
 * {@code List<QuizGeneration>}; the singular owner endpoint picks the
 * latest row from that list for backward compatibility.</p>
 */
public interface QuizGenerationService {

    /**
     * Enqueue a brand-new AI quiz generation request for the supplied
     * document. Each call creates a new {@link QuizGeneration} row — no
     * idempotent reuse of an existing row.
     *
     * <p>Initial status mapping by {@link AllowedDocumentFileType}:
     * <ul>
     *   <li>{@code PDF} → {@code QUEUED} (eligible for dispatch now)</li>
     *   <li>{@code DOC}, {@code DOCX} → {@code WAITING_SOURCE} (DOC/DOCX
     *       needs an extra readiness step before dispatch)</li>
     *   <li>{@code PPT}, {@code PPTX} → rejected with
     *       {@link IllegalArgumentException}; out of scope for Phase 2B.</li>
     * </ul>
     *
     * @param documentId             the just-persisted document id
     * @param documentFileId         the primary {@code DocumentFile} id
     *                               (must belong to {@code documentId})
     * @param fileType               resolved file type of the primary file
     * @param requestedQuestionCount question count in {@code [10, 50]}
     * @param focusTopic             optional owner-supplied focus. Pass
     *                               {@code null} for "whole document, no
     *                               focus". Blank values are normalised to
     *                               {@code null}. Length-capped at 500
     *                               characters.
     * @param now                    caller-supplied timestamp
     */
    QuizGeneration enqueueForDocument(
            UUID documentId,
            UUID documentFileId,
            AllowedDocumentFileType fileType,
            int requestedQuestionCount,
            String focusTopic,
            LocalDateTime now);

    /**
     * Return every {@link QuizGeneration} row attached to the supplied
     * document, ordered newest-first. Empty list means the document has
     * never been enqueued.
     */
    List<QuizGeneration> findAllByDocumentId(UUID documentId);

    /**
     * Cancel any active generation attached to a document. Called from
     * the soft-delete path.
     *
     * <p>Phase Multi Auto Quiz 1: iterates over every
     * {@link QuizGeneration} row of the document and applies the
     * per-row transition rules:</p>
     * <ul>
     *   <li>{@code WAITING_SOURCE}, {@code QUEUED}, {@code PROCESSING},
     *       {@code FAILED} → {@code CANCELLED}; {@code cancelledAt=now},
     *       {@code nextAttemptAt=null}; if {@code lastError} is blank,
     *       store {@code "DOCUMENT_DELETED"}.</li>
     *   <li>{@code READY} → no-op (terminal, must be preserved).</li>
     *   <li>{@code CANCELLED} → no-op (idempotent).</li>
     *   <li>No row → no-op.</li>
     * </ul>
     *
     * <p>READY rows are NEVER touched even if the document is being
     * soft-deleted, because the resulting {@link com.cmcu.itstudy.entity.Quiz}
     * is still surfaced to the owner elsewhere in the app.</p>
     */
    void cancelForDocument(UUID documentId, LocalDateTime now);

    /**
     * Phase 2C: promote every {@code WAITING_SOURCE} generation of a
     * document/file pair to {@code QUEUED} once its canonical FULL
     * preview PDF artifact becomes {@code READY}.
     *
     * <p>This method is the source-ready bridge: it is called from a
     * {@link org.springframework.transaction.support.TransactionSynchronization}
     * callback that fires <em>after</em> the FULL preview artifact's
     * {@code markReady} transaction commits. This guarantees the
     * READY state is durable before the generation is promoted.</p>
     *
     * <p>Phase Multi Auto Quiz 1: a document can have multiple
     * WAITING_SOURCE rows. The atomic UPDATE is allowed to promote
     * {@code N} rows in one statement; the caller is informed only via
     * the {@code affectedRows} log line.</p>
     *
     * <p>Transition rules per row:
     * <ul>
     *   <li>{@code WAITING_SOURCE} → {@code QUEUED}; {@code status=QUEUED},
     *       {@code updatedAt=now}. All other fields
     *       ({@code requestedQuestionCount}, {@code focusTopic},
     *       {@code attempts}, {@code requestedAt}, {@code documentId},
     *       {@code documentFileId}) are preserved.</li>
     *   <li>{@code QUEUED} → no-op (idempotent).</li>
     *   <li>{@code PROCESSING}, {@code READY}, {@code FAILED},
     *       {@code CANCELLED} → no-op.</li>
     *   <li>No row → no-op.</li>
     * </ul>
     *
     * <p>The {@code documentFileId} is a belt-and-suspenders safety
     * guard: the READY signal carries the artifact's
     * {@code documentFileId}; if a {@code QuizGeneration} exists for
     * {@code documentId} but is anchored to a different
     * {@code documentFileId}, the transition is skipped to prevent
     * cross-document pollution in future multi-file scenarios.</p>
     *
     * @param documentId     the document whose generation rows should be
     *                       promoted
     * @param documentFileId the primary file that is now READY; must match
     *                       each row's stored {@code documentFileId}
     * @param now           caller-supplied timestamp
     */
    void queueWhenSourceReady(UUID documentId, UUID documentFileId, LocalDateTime now);
}