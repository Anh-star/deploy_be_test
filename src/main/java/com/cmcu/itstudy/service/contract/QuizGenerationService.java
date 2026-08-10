package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.entity.QuizGeneration;
import com.cmcu.itstudy.enums.AllowedDocumentFileType;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 2B persistence service for AI quiz auto-generation requests.
 *
 * <p>Wired into the document-create transactions (free and paid) so the
 * upload-time intent is captured as a real database row instead of a log
 * line. No n8n call, no signed URL, no scheduler, no callback — those
 * belong to later phases and will read from
 * {@link QuizGeneration#getStatus()}.
 */
public interface QuizGenerationService {

    /**
     * Idempotently enqueue an AI quiz generation request for a freshly
     * persisted document.
     *
     * <p>If the repository already contains a row for
     * {@code documentId}, the existing row is returned unchanged — the
     * create flow must never overwrite or duplicate an active generation.
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
     * @param now                    caller-supplied timestamp
     */
    QuizGeneration enqueueForDocument(
            UUID documentId,
            UUID documentFileId,
            AllowedDocumentFileType fileType,
            int requestedQuestionCount,
            LocalDateTime now);

    /**
     * Look up the (at-most-one) generation row attached to a document.
     */
    Optional<QuizGeneration> findByDocumentId(UUID documentId);

    /**
     * Cancel any active generation attached to a document. Called from
     * the soft-delete path.
     *
     * <p>Transition rules:
     * <ul>
     *   <li>{@code WAITING_SOURCE}, {@code QUEUED}, {@code PROCESSING},
     *       {@code FAILED} → {@code CANCELLED}; {@code cancelledAt=now},
     *       {@code nextAttemptAt=null}; if {@code lastError} is blank,
     *       store {@code "DOCUMENT_DELETED"}.</li>
     *   <li>{@code READY} → no-op (terminal, must be preserved).</li>
     *   <li>{@code CANCELLED} → no-op (idempotent).</li>
     *   <li>No row → no-op.</li>
     * </ul>
     */
    void cancelForDocument(UUID documentId, LocalDateTime now);
}