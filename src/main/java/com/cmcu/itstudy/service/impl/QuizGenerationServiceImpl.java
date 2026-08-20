package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.entity.Document;
import com.cmcu.itstudy.entity.DocumentFile;
import com.cmcu.itstudy.entity.QuizGeneration;
import com.cmcu.itstudy.enums.AllowedDocumentFileType;
import com.cmcu.itstudy.enums.QuizGenerationStatus;
import com.cmcu.itstudy.repository.DocumentFileRepository;
import com.cmcu.itstudy.repository.DocumentRepository;
import com.cmcu.itstudy.repository.QuizGenerationRepository;
import com.cmcu.itstudy.service.contract.QuizGenerationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

/**
 * Default {@link QuizGenerationService} implementation.
 *
 * <p>Phase 2B + Phase Multi Auto Quiz 1: this service ONLY writes/reads
 * the {@code tbl_quiz_generations} table. It never makes a remote HTTP
 * call, never signs a Supabase URL, never touches n8n, never schedules
 * anything, and never calls the Gemini client. Those concerns belong to
 * later phases that will read the {@link QuizGeneration} row this service
 * populates.</p>
 *
 * <p>Multi-generation: each {@code enqueueForDocument} call creates a
 * brand-new row. {@code cancelForDocument} iterates over every row of
 * the document. {@code queueWhenSourceReady} is allowed to promote
 * multiple WAITING_SOURCE rows of the same document/file pair in one
 * atomic UPDATE.</p>
 *
 * <p>Propagation is {@code MANDATORY}: this bean is always called inside
 * an already-open transaction (the document create / soft-delete path)
 * so its writes participate in the same unit of work as the
 * {@code Document} and {@code DocumentFile} inserts.</p>
 */
@Service
public class QuizGenerationServiceImpl implements QuizGenerationService {

    /**
     * Single-source Vietnamese message used by every V1 Auto Quiz
     * entry point (free, paid, and direct service call) when the
     * uploader picked an unsupported file type. The existing
     * {@code GlobalExceptionHandler} maps {@link IllegalArgumentException}
     * to HTTP 400, so this string is the user-facing 400 payload.
     *
     * <p>Keep this {@code package-private} so the FREE / PAID binders
     * can re-use it without widening the public service surface.
     */
    static final String UNSUPPORTED_AUTO_QUIZ_MESSAGE =
            "Tự động tạo Quiz hiện chỉ hỗ trợ tài liệu PDF, DOC và DOCX.";

    private static final int FOCUS_TOPIC_MAX_LENGTH = 500;

    private final QuizGenerationRepository quizGenerationRepository;
    private final DocumentRepository documentRepository;
    private final DocumentFileRepository documentFileRepository;

    public QuizGenerationServiceImpl(
            QuizGenerationRepository quizGenerationRepository,
            DocumentRepository documentRepository,
            DocumentFileRepository documentFileRepository) {
        this.quizGenerationRepository = Objects.requireNonNull(quizGenerationRepository);
        this.documentRepository = Objects.requireNonNull(documentRepository);
        this.documentFileRepository = Objects.requireNonNull(documentFileRepository);
    }

    private static final Logger log =
            LoggerFactory.getLogger(QuizGenerationServiceImpl.class);

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public QuizGeneration enqueueForDocument(
            UUID documentId,
            UUID documentFileId,
            AllowedDocumentFileType fileType,
            int requestedQuestionCount,
            String focusTopic,
            LocalDateTime now) {

        // ── Argument validation ─────────────────────────────────────
        if (documentId == null) {
            throw new IllegalArgumentException("documentId must not be null");
        }
        if (documentFileId == null) {
            throw new IllegalArgumentException("documentFileId must not be null");
        }
        if (fileType == null) {
            throw new IllegalArgumentException(UNSUPPORTED_AUTO_QUIZ_MESSAGE);
        }
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }
        if (requestedQuestionCount < 10 || requestedQuestionCount > 50) {
            throw new IllegalArgumentException(
                    "requestedQuestionCount must be in [10, 50]");
        }

        String normalisedFocusTopic = normaliseFocusTopic(focusTopic);

        // ── Initial status mapping by file type ─────────────────────
        QuizGenerationStatus initialStatus;
        switch (fileType) {
            case PDF:
                initialStatus = QuizGenerationStatus.QUEUED;
                break;
            case DOC:
            case DOCX:
                initialStatus = QuizGenerationStatus.WAITING_SOURCE;
                break;
            case PPT:
            case PPTX:
                throw new IllegalArgumentException(UNSUPPORTED_AUTO_QUIZ_MESSAGE);
            default:
                throw new IllegalArgumentException(UNSUPPORTED_AUTO_QUIZ_MESSAGE);
        }

        // ── Load Document + DocumentFile and verify ownership ───────
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Document not found: " + documentId));
        DocumentFile documentFile = documentFileRepository.findById(documentFileId)
                .orElseThrow(() -> new NoSuchElementException(
                        "DocumentFile not found: " + documentFileId));

        if (documentFile.getDocument() == null
                || documentFile.getDocument().getId() == null
                || !documentFile.getDocument().getId().equals(documentId)) {
            throw new IllegalArgumentException(
                    "DocumentFile does not belong to the supplied document");
        }

        // ── Build & persist a NEW row ───────────────────────────────
        // Phase Multi Auto Quiz 1: no idempotent reuse — each call
        // produces an independent generation with its own requested
        // question count, optional focusTopic, status, dispatch token
        // and (eventually) resulting Quiz.
        QuizGeneration generation = QuizGeneration.builder()
                .document(document)
                .documentFile(documentFile)
                .requestedQuestionCount(requestedQuestionCount)
                .focusTopic(normalisedFocusTopic)
                .status(initialStatus)
                .attempts(0)
                .requestedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return quizGenerationRepository.saveAndFlush(generation);
    }

    /**
     * Normalise the owner-supplied focus topic:
     * <ul>
     *   <li>{@code null} → {@code null} (whole document, no focus);</li>
     *   <li>blank / whitespace-only → {@code null};</li>
     *   <li>otherwise trimmed and length-capped at
     *       {@link #FOCUS_TOPIC_MAX_LENGTH} characters.</li>
     * </ul>
     *
     * <p>Overflow &gt; {@link #FOCUS_TOPIC_MAX_LENGTH} is rejected with
     * {@link IllegalArgumentException} so the operator sees a clear 400
     * response rather than silent truncation.</p>
     */
    private static String normaliseFocusTopic(String focusTopic) {
        if (focusTopic == null) {
            return null;
        }
        String trimmed = focusTopic.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > FOCUS_TOPIC_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "focusTopic must not exceed "
                            + FOCUS_TOPIC_MAX_LENGTH + " characters");
        }
        return trimmed;
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuizGeneration> findAllByDocumentId(UUID documentId) {
        if (documentId == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(
                quizGenerationRepository.findAllByDocument_IdOrderByRequestedAtDesc(documentId));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelForDocument(UUID documentId, LocalDateTime now) {
        if (documentId == null) {
            throw new IllegalArgumentException("documentId must not be null");
        }
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }

        List<QuizGeneration> generations =
                quizGenerationRepository.findAllByDocument_IdOrderByRequestedAtDesc(documentId);
        if (generations.isEmpty()) {
            return;
        }

        for (QuizGeneration generation : generations) {
            QuizGenerationStatus current = generation.getStatus();
            if (current == null) {
                continue;
            }

            // READY and CANCELLED are terminal: must not be mutated
            // even in a multi-row loop. READY's Quiz must survive.
            if (current == QuizGenerationStatus.READY
                    || current == QuizGenerationStatus.CANCELLED) {
                continue;
            }

            generation.setStatus(QuizGenerationStatus.CANCELLED);
            generation.setCancelledAt(now);
            generation.setNextAttemptAt(null);
            if (generation.getLastError() == null
                    || generation.getLastError().isBlank()) {
                generation.setLastError("DOCUMENT_DELETED");
            }
        }

        quizGenerationRepository.saveAll(generations);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void queueWhenSourceReady(UUID documentId,
                                      UUID documentFileId,
                                      LocalDateTime now) {
        if (documentId == null) {
            throw new IllegalArgumentException("documentId must not be null");
        }
        if (documentFileId == null) {
            throw new IllegalArgumentException("documentFileId must not be null");
        }
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }

        // Phase 2C E2E FIX + Phase Multi Auto Quiz 1: the atomic
        // UPDATE is allowed to promote N rows in a single statement
        // (a document can now carry multiple WAITING_SOURCE rows).
        // The WHERE clause still guarantees:
        //   - document_id       must match
        //   - document_file_id  must match
        //   - status            must be WAITING_SOURCE
        // so QUEUED / PROCESSING / READY / FAILED / CANCELLED are
        // all impossible to overwrite, and CANCELLED is therefore
        // guaranteed to never resurrect.
        //
        // All other columns (requested_question_count, focus_topic,
        // attempts, requested_at, processing_at, ready_at, failed_at,
        // cancelled_at, last_error, ...) are preserved because they
        // are absent from the SET clause.
        int affected = quizGenerationRepository.promoteWaitingSourceToQueued(
                documentId, documentFileId,
                QuizGenerationStatus.WAITING_SOURCE, now);

        // Diagnostic logging for human E2E verification.  Does NOT
        // dump entity bodies, sensitive fields, or SQL. Phase Multi
        // Auto Quiz 1: affectedRows may now be 0, 1, …, N.
        log.info(
                "quiz-source-ready documentId={} documentFileId={} affectedRows={}",
                documentId, documentFileId, affected);
    }
}