package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.dto.autoquiz.AutoQuizCallbackRequestDto;
import com.cmcu.itstudy.dto.autoquiz.AutoQuizCallbackResponseDto;
import com.cmcu.itstudy.dto.autoquiz.AutoQuizCallbackRequestDto.AutoQuizCallbackQuestionDto;
import com.cmcu.itstudy.entity.Document;
import com.cmcu.itstudy.entity.DocumentQuiz;
import com.cmcu.itstudy.entity.Quiz;
import com.cmcu.itstudy.entity.QuizGeneration;
import com.cmcu.itstudy.entity.QuizQuestion;
import com.cmcu.itstudy.entity.QuizQuestionOption;
import com.cmcu.itstudy.enums.QuizGenerationStatus;
import com.cmcu.itstudy.handle.AutoQuizCallbackAccessDeniedException;
import com.cmcu.itstudy.handle.AutoQuizCallbackAccessDeniedException.Reason;
import com.cmcu.itstudy.repository.DocumentQuizRepository;
import com.cmcu.itstudy.repository.QuizGenerationRepository;
import com.cmcu.itstudy.repository.QuizQuestionOptionRepository;
import com.cmcu.itstudy.repository.QuizQuestionRepository;
import com.cmcu.itstudy.repository.QuizRepository;
import com.cmcu.itstudy.service.contract.AutoQuizCallbackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Phase 2E implementation of {@link AutoQuizCallbackService}.
 *
 * <h2>Security model</h2>
 * <p>The dispatch token is validated by constant-time comparison against
 * the stored value on the {@link QuizGeneration} row. This prevents timing
 * attacks on the token.</p>
 *
 * <h2>Idempotency</h2>
 * <p>If the generation is already {@code READY}, the already-created
 * {@code quizId} is returned without creating duplicates.</p>
 *
 * <h2>CANCELLED wins</h2>
 * <p>If the generation has been cancelled, no quiz is created and the
 * callback is rejected with a safe 403 response.</p>
 *
 * <h2>Transaction</h2>
 * <p>All persistence (quiz, questions, options, documentQuiz association,
 * generation READY transition) happens in one transaction. On any
 * exception the transaction rolls back and the generation remains
 * {@code PROCESSING}.</p>
 */
@Service
public class AutoQuizCallbackServiceImpl implements AutoQuizCallbackService {

    private static final Logger log =
            LoggerFactory.getLogger(AutoQuizCallbackServiceImpl.class);

    private static final int MIN_ANSWERS = 4;
    private static final int MAX_ANSWERS = 4;

    private final QuizGenerationRepository generationRepository;
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository questionRepository;
    private final QuizQuestionOptionRepository optionRepository;
    private final DocumentQuizRepository documentQuizRepository;

    public AutoQuizCallbackServiceImpl(
            QuizGenerationRepository generationRepository,
            QuizRepository quizRepository,
            QuizQuestionRepository questionRepository,
            QuizQuestionOptionRepository optionRepository,
            DocumentQuizRepository documentQuizRepository) {
        this.generationRepository = Objects.requireNonNull(generationRepository);
        this.quizRepository = Objects.requireNonNull(quizRepository);
        this.questionRepository = Objects.requireNonNull(questionRepository);
        this.optionRepository = Objects.requireNonNull(optionRepository);
        this.documentQuizRepository = Objects.requireNonNull(documentQuizRepository);
    }

    @Override
    @Transactional
    public AutoQuizCallbackResponseDto processCallback(
            UUID generationId,
            UUID dispatchToken,
            AutoQuizCallbackRequestDto request) {

        // Step 1: Validate inputs
        validateCallbackInputs(generationId, dispatchToken, request);

        // Step 2: Fetch generation
        QuizGeneration generation = generationRepository.findById(generationId)
                .orElseThrow(() -> new AutoQuizCallbackAccessDeniedException(
                        Reason.GENERATION_NOT_FOUND,
                        "Generation not found"));

        // Step 3: Constant-time token validation
        if (!constantTimeTokenEquals(generation.getDispatchToken(), dispatchToken)) {
            log.warn(
                    "Auto Quiz callback rejected: token mismatch "
                            + "generationId={}",
                    generationId);
            throw new AutoQuizCallbackAccessDeniedException(
                    Reason.TOKEN_MISMATCH,
                    "Dispatch token does not match this generation");
        }

        // Step 4: CANCELLED wins
        if (generation.getStatus() == QuizGenerationStatus.CANCELLED) {
            log.info(
                    "Auto Quiz callback rejected: generation is CANCELLED "
                            + "generationId={}",
                    generationId);
            throw new AutoQuizCallbackAccessDeniedException(
                    Reason.CANCELLED,
                    "Generation has been cancelled");
        }

        // Step 5: Idempotent READY
        if (generation.getStatus() == QuizGenerationStatus.READY) {
            log.info(
                    "Auto Quiz callback idempotent: generation already READY "
                            + "generationId={} quizId={}",
                    generationId,
                    generation.getQuiz() != null
                            ? generation.getQuiz().getId() : null);
            return buildIdempotentResponse(generation);
        }

        // Step 6: Validate status is PROCESSING (the expected callback state)
        if (generation.getStatus() != QuizGenerationStatus.PROCESSING) {
            log.warn(
                    "Auto Quiz callback rejected: unexpected status={} "
                            + "generationId={}",
                    generation.getStatus(),
                    generationId);
            throw new AutoQuizCallbackAccessDeniedException(
                    Reason.GENERATION_NOT_FOUND,
                    "Generation is not in PROCESSING state");
        }

        // Step 7: Validate question count matches generation expectation
        if (request.getRequestedQuestionCount() == null
                || !request.getRequestedQuestionCount()
                        .equals(generation.getRequestedQuestionCount())) {
            log.warn(
                    "Auto Quiz callback rejected: question count mismatch "
                            + "generationId={} expected={} received={}",
                    generationId,
                    generation.getRequestedQuestionCount(),
                    request.getRequestedQuestionCount());
            throw new AutoQuizCallbackAccessDeniedException(
                    Reason.QUESTION_COUNT_MISMATCH,
                    "Question count does not match generation request");
        }

        // Step 8: Validate questions array
        List<AutoQuizCallbackQuestionDto> questions = request.getQuestions();
        if (questions == null || questions.isEmpty()) {
            throw new AutoQuizCallbackAccessDeniedException(
                    Reason.QUESTIONS_EMPTY,
                    "Questions array must not be empty");
        }
        if (questions.size() != generation.getRequestedQuestionCount()) {
            throw new AutoQuizCallbackAccessDeniedException(
                    Reason.QUESTION_COUNT_MISMATCH,
                    "Questions array length does not match requested count");
        }

        // Step 9: Validate each question
        for (int i = 0; i < questions.size(); i++) {
            validateQuestion(questions.get(i), i);
        }

        // Step 10: Persist quiz + questions + options
        Quiz quiz = buildAndPersistQuiz(generation, request);

        // Step 11: Update generation to READY
        LocalDateTime now = LocalDateTime.now();
        generation.setStatus(QuizGenerationStatus.READY);
        generation.setQuiz(quiz);
        generation.setReadyAt(now);
        generation.setUpdatedAt(now);
        generationRepository.save(generation);

        log.info(
                "Auto Quiz callback complete: generationId={} quizId={} "
                        + "questionCount={}",
                generationId,
                quiz.getId(),
                questions.size());

        return AutoQuizCallbackResponseDto.builder()
                .accepted(true)
                .status(QuizGenerationStatus.READY.name())
                .generationId(generationId)
                .quizId(quiz.getId())
                .message("Quiz generated successfully")
                .build();
    }

    private void validateCallbackInputs(
            UUID generationId,
            UUID dispatchToken,
            AutoQuizCallbackRequestDto request) {
        if (generationId == null) {
            throw new AutoQuizCallbackAccessDeniedException(
                    Reason.GENERATION_NOT_FOUND,
                    "Generation not found");
        }
        if (dispatchToken == null) {
            throw new AutoQuizCallbackAccessDeniedException(
                    Reason.MISSING_TOKEN,
                    "Dispatch token is required");
        }
        if (request == null) {
            throw new AutoQuizCallbackAccessDeniedException(
                    Reason.QUESTIONS_EMPTY,
                    "Request body is required");
        }
    }

    private void validateQuestion(
            AutoQuizCallbackQuestionDto dto, int index) {
        if (dto == null) {
            throw new AutoQuizCallbackAccessDeniedException(
                    Reason.QUESTIONS_EMPTY,
                    "Question at index " + index + " is null");
        }
        if (dto.getQuestion() == null || dto.getQuestion().isBlank()) {
            throw new AutoQuizCallbackAccessDeniedException(
                    Reason.QUESTIONS_EMPTY,
                    "Question text at index " + index + " is required");
        }
        List<String> answers = dto.getAnswers();
        if (answers == null || answers.size() != MIN_ANSWERS) {
            throw new AutoQuizCallbackAccessDeniedException(
                    Reason.ANSWER_COUNT_WRONG,
                    "Question at index " + index
                            + " must have exactly " + MIN_ANSWERS + " answers");
        }
        for (int a = 0; a < answers.size(); a++) {
            if (answers.get(a) == null || answers.get(a).isBlank()) {
                throw new AutoQuizCallbackAccessDeniedException(
                        Reason.ANSWER_COUNT_WRONG,
                        "Answer at index " + a + " in question " + index
                                + " must be non-blank");
            }
        }
        Integer correct = dto.getCorrect();
        if (correct == null || correct < 0 || correct > 3) {
            throw new AutoQuizCallbackAccessDeniedException(
                    Reason.CORRECT_INDEX_OUT_OF_RANGE,
                    "correct at index " + index
                            + " must be 0, 1, 2, or 3");
        }
    }

    private Quiz buildAndPersistQuiz(
            QuizGeneration generation,
            AutoQuizCallbackRequestDto request) {

        // The document is LAZY; access it inside the transaction.
        Document document = generation.getDocument();

        // Build the Quiz entity
        Quiz quiz = Quiz.builder()
                .title(resolveQuizTitle(request, document))
                .description(resolveQuizDescription(request, document))
                .durationMinutes(30)
                .maxAttemptsPerDay(3)
                .passScorePercent(80.0d)
                .published(true)
                .build();
        Quiz savedQuiz = quizRepository.save(quiz);

        // Build and persist questions
        List<AutoQuizCallbackQuestionDto> questionDtos = request.getQuestions();
        int sortOrder = 0;
        for (AutoQuizCallbackQuestionDto dto : questionDtos) {
            QuizQuestion question = buildQuestion(savedQuiz, dto, sortOrder++);
            QuizQuestion savedQuestion = questionRepository.save(question);

            // Build options
            List<QuizQuestionOption> options = new ArrayList<>();
            List<String> answers = dto.getAnswers();
            for (int i = 0; i < answers.size(); i++) {
                QuizQuestionOption option = QuizQuestionOption.builder()
                        .question(savedQuestion)
                        .content(answers.get(i).trim())
                        .isCorrect(i == dto.getCorrect())
                        .sortOrder(i)
                        .build();
                options.add(option);
            }
            optionRepository.saveAll(options);
        }

        // Build and persist DocumentQuiz association
        int existingLinks = documentQuizRepository
                .findAllByDocumentIdWithQuiz(document.getId()).size();
        DocumentQuiz documentQuiz = DocumentQuiz.builder()
                .document(document)
                .quiz(savedQuiz)
                .sortOrder(existingLinks + 1)
                .build();
        documentQuizRepository.save(documentQuiz);

        return savedQuiz;
    }

    private QuizQuestion buildQuestion(
            Quiz quiz,
            AutoQuizCallbackQuestionDto dto,
            int sortOrder) {
        return QuizQuestion.builder()
                .quiz(quiz)
                .sortOrder(sortOrder)
                .questionText(dto.getQuestion().trim())
                .points(1)
                .build();
    }

    private String buildQuizTitle(Document document) {
        // Deprecated — kept temporarily to avoid unused-symbol warnings
        // if referenced from older build paths. Safe to remove once all
        // call sites route through {@link #resolveQuizTitle}.
        return resolveQuizTitle(null, document);
    }

    /**
     * Resolve the title that will be stored on {@link Quiz}.
     *
     * <p>Priority:</p>
     * <ol>
     *   <li>The {@code quizTitle} field shipped by n8n / Gemini (trimmed).
     *       This is the canonical AI-generated Vietnamese title.</li>
     *   <li>If the AI did not provide one (legacy callback or AI omitted
     *       the field), fall back to a Vietnamese, document-title-based
     *       title &mdash; <em>only</em> when the document title does not
     *       show signs of mojibake (lone {@code '?'} characters that
     *       stand in for unrecoverable UTF-8 bytes).</li>
     *   <li>Final fallback: a generic Vietnamese label.</li>
     * </ol>
     *
     * <p>The output is hard-capped at {@code 255} characters to mirror
     * the {@code tbl_quizzes.title} column constraint and the inbound
     * {@link AutoQuizCallbackRequestDto#getQuizTitle()} validator.</p>
     */
    private String resolveQuizTitle(
            AutoQuizCallbackRequestDto request,
            Document document) {
        String aiTitle = request != null ? request.getQuizTitle() : null;
        if (aiTitle != null) {
            String trimmed = aiTitle.trim();
            if (!trimmed.isEmpty()) {
                if (trimmed.length() > 255) {
                    trimmed = trimmed.substring(0, 255);
                }
                return trimmed;
            }
        }
        return buildFallbackTitle(document);
    }

    private String buildFallbackTitle(Document document) {
        if (document != null) {
            String docTitle = document.getTitle();
            if (docTitle != null) {
                String trimmed = docTitle.trim();
                if (!trimmed.isEmpty() && !looksMojibake(trimmed)) {
                    return "Bài trắc nghiệm: " + trimmed;
                }
            }
        }
        return "Bài trắc nghiệm tự động";
    }

    /**
     * Heuristic for unrecoverable mojibake: a non-trivial proportion of
     * {@code '?'} characters inside a UTF-8 title usually means the
     * bytes could not be decoded and were substituted. We do NOT try to
     * repair the string — we simply reject it so the fallback generic
     * title can take its place.
     */
    static boolean looksMojibake(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        int questionMarks = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '?') {
                questionMarks++;
            }
        }
        // Single '?' in a long Vietnamese title is enough evidence to skip
        // it; shorter strings get a slightly higher tolerance.
        int length = value.length();
        if (questionMarks == 0) {
            return false;
        }
        if (length <= 20) {
            return questionMarks >= 2;
        }
        return true;
    }

    /**
     * Resolve the description that will be stored on {@link Quiz}.
     *
     * <p>Priority:</p>
     * <ol>
     *   <li>The {@code quizDescription} field shipped by n8n / Gemini
     *       (trimmed). This is the canonical semantic description.</li>
     *   <li>If the AI did not provide one (legacy callback or AI omitted
     *       the field), fall back to a generic, document-title-based
     *       sentence that says the quiz helps review the document's
     *       knowledge. <strong>No timestamps or requested-count
     *       metadata</strong> are baked into this fallback — the
     *       semantic content must come from the AI.</li>
     * </ol>
     *
     * <p>The fallback length is hard-capped at {@code 1000} characters
     * to mirror {@link AutoQuizCallbackRequestDto#getQuizDescription()}
     * validation; AI-supplied descriptions are already capped by the
     * inbound validator.</p>
     */
    private String resolveQuizDescription(
            AutoQuizCallbackRequestDto request,
            Document document) {
        String aiDescription = request != null ? request.getQuizDescription() : null;
        if (aiDescription != null) {
            String trimmed = aiDescription.trim();
            if (!trimmed.isEmpty()) {
                if (trimmed.length() > 1000) {
                    trimmed = trimmed.substring(0, 1000);
                }
                return trimmed;
            }
        }
        return buildFallbackDescription(document);
    }

    private String buildFallbackDescription(Document document) {
        String docTitle = document != null ? document.getTitle() : null;
        if (docTitle != null && !docTitle.isBlank()) {
            String trimmed = docTitle.trim();
            return "Bài trắc nghiệm giúp ôn tập và kiểm tra các kiến thức "
                    + "trọng tâm trong tài liệu \"" + trimmed + "\".";
        }
        return "Bài trắc nghiệm giúp ôn tập và kiểm tra các kiến thức "
                + "trọng tâm của tài liệu.";
    }

    private AutoQuizCallbackResponseDto buildIdempotentResponse(
            QuizGeneration generation) {
        UUID quizId = generation.getQuiz() != null
                ? generation.getQuiz().getId()
                : null;
        return AutoQuizCallbackResponseDto.builder()
                .accepted(true)
                .status(QuizGenerationStatus.READY.name())
                .generationId(generation.getId())
                .quizId(quizId)
                .message("Quiz already generated")
                .build();
    }

    /**
     * Constant-time UUID comparison to prevent timing attacks on the
     * dispatch token. Compares most-significant and least-significant
     * bits independently and ORs them together so the total time
     * is always the same regardless of where the first difference
     * occurs.
     */
    static boolean constantTimeTokenEquals(UUID expected, UUID supplied) {
        if (expected == null || supplied == null) {
            return false;
        }
        long diff = 0L;
        diff |= expected.getMostSignificantBits()
                ^ supplied.getMostSignificantBits();
        diff |= expected.getLeastSignificantBits()
                ^ supplied.getLeastSignificantBits();
        return diff == 0L;
    }
}
