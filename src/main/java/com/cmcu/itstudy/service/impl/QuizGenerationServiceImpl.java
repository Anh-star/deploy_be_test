package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.entity.Document;
import com.cmcu.itstudy.entity.DocumentFile;
import com.cmcu.itstudy.entity.Quiz;
import com.cmcu.itstudy.entity.QuizGeneration;
import com.cmcu.itstudy.entity.User;
import com.cmcu.itstudy.enums.AllowedDocumentFileType;
import com.cmcu.itstudy.enums.QuizGenerationStatus;
import com.cmcu.itstudy.handle.AutoQuizAlreadyHasAttemptsException;
import com.cmcu.itstudy.handle.AutoQuizGenerationNotInTerminalStateException;
import com.cmcu.itstudy.repository.DocumentFileRepository;
import com.cmcu.itstudy.repository.DocumentQuizRepository;
import com.cmcu.itstudy.repository.DocumentRepository;
import com.cmcu.itstudy.repository.QuizAttemptRepository;
import com.cmcu.itstudy.repository.QuizGenerationRepository;
import com.cmcu.itstudy.repository.QuizQuestionOptionRepository;
import com.cmcu.itstudy.repository.QuizQuestionRepository;
import com.cmcu.itstudy.repository.QuizRepository;
import com.cmcu.itstudy.service.contract.QuizGenerationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
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
    private final QuizRepository quizRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final DocumentQuizRepository documentQuizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizQuestionOptionRepository quizQuestionOptionRepository;

    public QuizGenerationServiceImpl(
            QuizGenerationRepository quizGenerationRepository,
            DocumentRepository documentRepository,
            DocumentFileRepository documentFileRepository,
            QuizRepository quizRepository,
            QuizAttemptRepository quizAttemptRepository,
            DocumentQuizRepository documentQuizRepository,
            QuizQuestionRepository quizQuestionRepository,
            QuizQuestionOptionRepository quizQuestionOptionRepository) {
        this.quizGenerationRepository = Objects.requireNonNull(quizGenerationRepository);
        this.documentRepository = Objects.requireNonNull(documentRepository);
        this.documentFileRepository = Objects.requireNonNull(documentFileRepository);
        this.quizRepository = Objects.requireNonNull(quizRepository);
        this.quizAttemptRepository = Objects.requireNonNull(quizAttemptRepository);
        this.documentQuizRepository = Objects.requireNonNull(documentQuizRepository);
        this.quizQuestionRepository = Objects.requireNonNull(quizQuestionRepository);
        this.quizQuestionOptionRepository = Objects.requireNonNull(quizQuestionOptionRepository);
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

    /**
     * Phase 6C — owner-initiated safe delete of a single
     * {@link QuizGeneration} row and its associated {@link Quiz}
     * (only when the generation is {@code READY}).
     *
     * <p>The flow follows the order mandated by the FK constraints
     * documented in Phase 6B:</p>
     *
     * <pre>{@code
     *   STEP 0  lock generation row (PESSIMISTIC_WRITE)
     *   STEP 1  validate documentId binding + ownership
     *   STEP 2  branch on status
     *           - WAITING_SOURCE / QUEUED / PROCESSING → 409
     *           - FAILED / CANCELLED → step 3
     *           - READY → step 4
     *   STEP 3  defensive check: generation.quiz must be null
     *           delete generation row
     *   STEP 4  lock Quiz row (PESSIMISTIC_WRITE)
     *   STEP 5  attempt guard: existsByQuiz_Id → 409
     *   STEP 6  break generation -> Quiz FK
     *           (set generation.quiz = null + flush)
     *   STEP 7  delete DocumentQuiz link (documentId + quizId)
     *   STEP 8  delete QuizQuestionOption rows for quizId
     *   STEP 9  delete QuizQuestion rows for quizId
     *   STEP 10 delete Quiz row
     *   STEP 11 delete generation row
     * }</pre>
     *
     * <p>Steps 6–11 run inside the same transaction; a failure in any
     * later step rolls back the entire delete. Steps 7/8/9 use bulk
     * {@code @Modifying} JPQL DELETEs that already {@code flush} and
     * {@code clear}, so the {@code Quiz} row is not stale-managed by
     * JPA when {@code quizRepository.delete(quiz)} runs.</p>
     */
    @Override
    @Transactional(
            propagation = Propagation.REQUIRED,
            isolation = Isolation.READ_COMMITTED)
    public void deleteForOwner(
            UUID documentId,
            UUID generationId,
            User currentUser) {

        // ── Argument guards ─────────────────────────────────────────
        if (documentId == null) {
            throw new IllegalArgumentException("documentId must not be null");
        }
        if (generationId == null) {
            throw new IllegalArgumentException("generationId must not be null");
        }
        if (currentUser == null || currentUser.getId() == null) {
            throw new IllegalArgumentException("currentUser must not be null");
        }

        // ── STEP 0: lock generation row ─────────────────────────────
        QuizGeneration generation = quizGenerationRepository
                .findByIdForUpdate(generationId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Quiz generation not found with id: " + generationId));

        // ── STEP 1: validate documentId binding + ownership ────────
        Document document = generation.getDocument();
        if (document == null || document.getId() == null
                || !document.getId().equals(documentId)) {
            // Treat cross-document-id mismatch as not-found to avoid
            // leaking resource existence.
            throw new NoSuchElementException(
                    "Quiz generation not found with id: " + generationId);
        }
        if (document.getCreatedBy() == null
                || document.getCreatedBy().getId() == null
                || !document.getCreatedBy().getId().equals(currentUser.getId())) {
            // Phase 6C.1 hotfix: throw Spring Security's
            // AccessDeniedException so GlobalExceptionHandler maps it
            // to HTTP 403 (the project convention for forbidden
            // access). The earlier SecurityException variant was
            // misclassified as 500 because the existing
            // GlobalExceptionHandler does not handle java.lang.SecurityException
            // explicitly and the generic Exception handler kicks in.
            //
            // Scoped to deleteForOwner ONLY — DocumentServiceImpl
            // (updateDocument / deleteDocument) keeps its existing
            // SecurityException path untouched, per the hotfix
            // contract.
            throw new AccessDeniedException(
                    "Bạn không có quyền xóa bài đánh giá này.");
        }

        // ── STEP 2: branch on status ────────────────────────────────
        QuizGenerationStatus status = generation.getStatus();
        if (status == null) {
            // Defensive — should not happen because @Enumerated maps
            // a non-null column, but if a row ever drifts, reject.
            throw new AutoQuizGenerationNotInTerminalStateException(
                    "Không thể xóa bài đánh giá đang được xử lý.");
        }
        switch (status) {
            case WAITING_SOURCE:
            case QUEUED:
            case PROCESSING:
                throw new AutoQuizGenerationNotInTerminalStateException(
                        "Không thể xóa bài đánh giá đang được xử lý.");
            case FAILED:
            case CANCELLED:
                deleteFailedOrCancelled(generation);
                return;
            case READY:
                deleteReady(generation, documentId);
                return;
            default:
                throw new AutoQuizGenerationNotInTerminalStateException(
                        "Không thể xóa bài đánh giá đang được xử lý.");
        }
    }

    /**
     * Step 3 — delete a terminal {@code FAILED} / {@code CANCELLED}
     * generation. The Phase 6B audit guarantees
     * {@code generation.quiz == null} in both cases; if that invariant
     * is ever violated we refuse to silently cascade into a Quiz.
     */
    private void deleteFailedOrCancelled(QuizGeneration generation) {
        Quiz quiz = generation.getQuiz();
        if (quiz != null) {
            log.error(
                    "Refusing to delete terminal generation {} because it "
                            + "unexpectedly has a non-null Quiz reference {} — "
                            + "this would cascade outside the contract.",
                    generation.getId(), quiz.getId());
            throw new IllegalStateException(
                    "Generation state is inconsistent: FAILED/CANCELLED with "
                            + "non-null Quiz reference.");
        }
        // No cascading dependency on this row.
        quizGenerationRepository.delete(generation);
    }

    /**
     * Step 4–11 — delete a READY generation and its associated Quiz,
     * guarded against QuizAttempt history.
     */
    private void deleteReady(QuizGeneration generation, UUID documentId) {
        Quiz quiz = generation.getQuiz();
        if (quiz == null) {
            // READY without a Quiz is a contract violation; refuse.
            log.error(
                    "Refusing to delete READY generation {} because it has "
                            + "no Quiz reference — this violates the READY "
                            + "contract.",
                    generation.getId());
            throw new IllegalStateException(
                    "Generation state is inconsistent: READY with null Quiz.");
        }
        UUID quizId = quiz.getId();

        // ── STEP 4: lock Quiz row ───────────────────────────────────
        Quiz lockedQuiz = quizRepository.findByIdForUpdate(quizId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Quiz not found with id: " + quizId));

        // ── STEP 5: attempt guard ───────────────────────────────────
        if (quizAttemptRepository.existsByQuiz_Id(lockedQuiz.getId())) {
            throw new AutoQuizAlreadyHasAttemptsException(
                    "Bài đánh giá đã có người làm nên không thể xóa.");
        }

        // ── STEP 6: break generation -> Quiz FK ─────────────────────
        // Set quiz_id = NULL on the generation row first. The bulk
        // DELETEs in the next steps need the Quiz row detached from
        // any foreign-key inbound, and JPA's cascade=REMOVE on
        // Quiz.questions is intentionally NOT relied on (per the
        // Phase 6B audit).
        generation.setQuiz(null);
        quizGenerationRepository.saveAndFlush(generation);

        // ── STEP 7: delete DocumentQuiz link ────────────────────────
        documentQuizRepository.deleteByDocument_IdAndQuiz_Id(
                documentId, quizId);

        // ── STEP 8: delete QuizQuestionOption rows ──────────────────
        // Must run BEFORE deleting questions, otherwise
        // tbl_quiz_question_options.question_id FK blocks the question
        // delete.
        quizQuestionOptionRepository.deleteByQuestion_Quiz_Id(quizId);

        // ── STEP 9: delete QuizQuestion rows ────────────────────────
        quizQuestionRepository.deleteByQuiz_Id(quizId);

        // ── STEP 10: delete Quiz row ────────────────────────────────
        // By this point every inbound FK to tbl_quizzes.id has been
        // dropped:
        //   - tbl_quiz_generations.quiz_id     → NULL via step 6
        //   - tbl_document_quizzes.quiz_id     → 0 rows via step 7
        //   - tbl_quiz_questions.quiz_id       → 0 rows via step 9
        //   - tbl_quiz_question_options.question_id → 0 rows via step 8
        //   - tbl_quiz_attempts.quiz_id        → 0 rows via step 5 guard
        //   - tbl_quiz_attempt_answers.*       → tied to attempts,
        //                                       so already 0 by step 5
        quizRepository.delete(lockedQuiz);

        // ── STEP 11: delete generation row ──────────────────────────
        quizGenerationRepository.delete(generation);

        log.info(
                "Auto Quiz deleted: generationId={} documentId={} quizId={}",
                generation.getId(), documentId, quizId);
    }
}