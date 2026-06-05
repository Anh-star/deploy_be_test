package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.dto.document.DocumentDetailQuizDto;
import com.cmcu.itstudy.dto.document.QuizListPageResponseDto;
import com.cmcu.itstudy.dto.quiz.QuizHistoryItemDto;
import com.cmcu.itstudy.dto.quiz.QuizHistoryPageResponseDto;
import com.cmcu.itstudy.dto.quiz.QuizHistorySummaryDto;
import com.cmcu.itstudy.dto.quiz.QuizPreviewResponseDto;
import com.cmcu.itstudy.dto.quiz.QuizResultResponseDto;
import com.cmcu.itstudy.dto.quiz.StartQuizResponseDto;
import com.cmcu.itstudy.dto.quiz.SubmitQuizAnswerRequestDto;
import com.cmcu.itstudy.dto.quiz.SubmitQuizRequestDto;
import com.cmcu.itstudy.entity.DocumentQuiz;
import com.cmcu.itstudy.entity.Quiz;
import com.cmcu.itstudy.entity.QuizAttempt;
import com.cmcu.itstudy.entity.QuizAttemptAnswer;
import com.cmcu.itstudy.entity.QuizQuestion;
import com.cmcu.itstudy.entity.QuizQuestionOption;
import com.cmcu.itstudy.handle.QuizAlreadySubmittedException;
import com.cmcu.itstudy.mapper.DocumentMapper;
import com.cmcu.itstudy.mapper.QuizMapper;
import com.cmcu.itstudy.repository.DocumentRepository;
import com.cmcu.itstudy.repository.QuizAttemptAnswerRepository;
import com.cmcu.itstudy.repository.QuizAttemptRepository;
import com.cmcu.itstudy.repository.QuizRepository;
import com.cmcu.itstudy.repository.DocumentQuizRepository;
import com.cmcu.itstudy.repository.QuizQuestionRepository;
import com.cmcu.itstudy.security.UserDetailsImpl;
import com.cmcu.itstudy.service.contract.QuizService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class QuizServiceImpl implements QuizService {

    private static final int PREVIEW_HISTORY_LIMIT = 5;
    private static final Logger log = LoggerFactory.getLogger(QuizServiceImpl.class);

    private final DocumentQuizRepository documentQuizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizRepository quizRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizAttemptAnswerRepository quizAttemptAnswerRepository;
    private final DocumentRepository documentRepository;

    public QuizServiceImpl(DocumentQuizRepository documentQuizRepository,
                           QuizQuestionRepository quizQuestionRepository,
                           QuizRepository quizRepository,
                           QuizAttemptRepository quizAttemptRepository,
                           QuizAttemptAnswerRepository quizAttemptAnswerRepository,
                           DocumentRepository documentRepository) {
        this.documentQuizRepository = documentQuizRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.quizRepository = quizRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.quizAttemptAnswerRepository = quizAttemptAnswerRepository;
        this.documentRepository = documentRepository;
    }

    @Transactional(readOnly = true)
    @Override
    public List<DocumentDetailQuizDto> loadQuizzesForDocument(UUID documentId) {
        List<DocumentQuiz> links = documentQuizRepository.findAllByDocumentIdWithQuiz(documentId);
        if (links.isEmpty()) {
            return Collections.emptyList();
        }

        List<UUID> quizIds = links.stream()
                .map(DocumentQuiz::getQuiz)
                .filter(Objects::nonNull)
                .map(Quiz::getId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        List<Object[]> countRows = quizIds.isEmpty()
                ? Collections.emptyList()
                : quizQuestionRepository.countQuestionsGroupedByQuizId(quizIds);
        Map<UUID, Long> counts = DocumentMapper.toQuizQuestionCountMap(countRows);

        return links.stream()
                .map(DocumentQuiz::getQuiz)
                .filter(Objects::nonNull)
                .map(q -> DocumentMapper.toQuizDto(q, counts.getOrDefault(q.getId(), 0L)))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @Override
    public QuizListPageResponseDto getQuizzesByDocument(UUID documentId, int page, int size) {
        if (!documentRepository.existsById(documentId)) {
            throw new NoSuchElementException("Document not found");
        }

        int safePage = Math.max(page, 0);
        int safeSize = size > 0 ? size : 10;
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by("sortOrder").ascending().and(Sort.by("id").ascending()));
        Page<DocumentQuiz> linkPage = documentQuizRepository.findByDocumentIdWithQuiz(documentId, pageable);

        List<UUID> quizIds = linkPage.getContent().stream()
                .map(DocumentQuiz::getQuiz)
                .filter(Objects::nonNull)
                .map(Quiz::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<Object[]> countRows = quizIds.isEmpty()
                ? Collections.emptyList()
                : quizQuestionRepository.countQuestionsGroupedByQuizId(quizIds);
        Map<UUID, Long> counts = DocumentMapper.toQuizQuestionCountMap(countRows);

        return DocumentMapper.toQuizListPageResponseDto(linkPage, counts);
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public StartQuizResponseDto startQuiz(UUID quizId) {
        UUID userId = getCurrentUserId();
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new NoSuchElementException("Quiz not found"));

        QuizAttempt inProgressAttempt = findLatestInProgressAttemptForStart(userId, quizId);
        if (inProgressAttempt != null) {
            return toStartQuizResponseDto(inProgressAttempt, quiz);
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        long todayAttempts = quizAttemptRepository.countByUserIdAndQuizIdAndStartTimeBetween(
                userId,
                quizId,
                startOfDay,
                endOfDay
        );

        int attemptNumber = (int) todayAttempts + 1;
        QuizAttempt attempt = QuizAttempt.builder()
                .userId(userId)
                .quiz(quiz)
                .attemptNumber(attemptNumber)
                .startTime(now)
                .status("IN_PROGRESS")
                .build();
        try {
            QuizAttempt saved = quizAttemptRepository.saveAndFlush(attempt);
            return toStartQuizResponseDto(saved, quiz);
        } catch (DataIntegrityViolationException ex) {
            // Unique constraint race fallback: if another request created IN_PROGRESS first, return that one.
            QuizAttempt existing = findLatestInProgressAttemptForStart(userId, quizId);
            if (existing != null) {
                return toStartQuizResponseDto(existing, quiz);
            }
            throw ex;
        }
    }

    @Override
    @Transactional
    public QuizResultResponseDto submitQuiz(SubmitQuizRequestDto request) {
        try {
            UUID userId = getCurrentUserId();
            UUID attemptId = parseUuid(request != null ? request.getAttemptId() : null, "Invalid attemptId");

            QuizAttempt attempt = quizAttemptRepository.findByIdForUpdate(attemptId)
                    .orElseThrow(() -> new NoSuchElementException("Attempt not found"));
            validateAttemptOwner(attempt, userId);
            if (attempt.getEndTime() != null) {
                throw new QuizAlreadySubmittedException("Attempt already submitted");
            }
            if (!"IN_PROGRESS".equalsIgnoreCase(attempt.getStatus())) {
                throw new QuizAlreadySubmittedException("Attempt is not in progress");
            }

            Quiz quiz = attempt.getQuiz();
            if (quiz == null || quiz.getId() == null) {
                throw new NoSuchElementException("Quiz not found");
            }

            List<QuizQuestion> questions = quizQuestionRepository.findAllByQuizIdWithOptions(quiz.getId());
            Map<UUID, QuizQuestionOption> selectedByQuestionId = resolveSelectedOptions(questions, request);

            int totalQuestions = questions.size();
            int correctCount = 0;
            int wrongCount = 0;
            int skippedCount = 0;
            double score = 0d;
            double maxScore = 0d;

            List<QuizAttemptAnswer> answersToSave = new java.util.ArrayList<>(questions.size());
            for (QuizQuestion question : questions) {
                int points = question.getPoints() != null ? question.getPoints() : 1;
                maxScore += points;

                QuizQuestionOption selected = selectedByQuestionId.get(question.getId());
                Boolean isCorrect;
                if (selected == null) {
                    skippedCount++;
                    isCorrect = Boolean.FALSE;
                } else if (Boolean.TRUE.equals(selected.getIsCorrect())) {
                    correctCount++;
                    score += points;
                    isCorrect = Boolean.TRUE;
                } else {
                    wrongCount++;
                    isCorrect = Boolean.FALSE;
                }

                answersToSave.add(QuizAttemptAnswer.builder()
                        .attempt(attempt)
                        .question(question)
                        .selectedOption(selected)
                        .isCorrect(isCorrect)
                        .build());
            }

            quizAttemptAnswerRepository.saveAll(answersToSave);
            attempt.setAnswers(answersToSave);
            attempt.setTotalQuestions(totalQuestions);
            attempt.setCorrectCount(correctCount);
            attempt.setWrongCount(wrongCount);
            attempt.setSkippedCount(skippedCount);
            attempt.setScore(score);
            attempt.setMaxScore(maxScore);
            double scorePercent = maxScore > 0 ? (score * 100d / maxScore) : 0d;
            attempt.setScorePercent(scorePercent);
            attempt.setEndTime(LocalDateTime.now());
            double passScorePercent = quiz.getPassScorePercent() != null ? quiz.getPassScorePercent() : 80.0d;
            attempt.setStatus(scorePercent >= passScorePercent ? "PASSED" : "FAILED");
            QuizAttempt saved = quizAttemptRepository.save(attempt);

            return QuizMapper.toQuizResultResponseDto(saved);
        } catch (Exception e) {
            log.error("Submit quiz error", e);
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public QuizResultResponseDto getQuizResult(UUID attemptId) {
        UUID userId = getCurrentUserId();
        QuizAttempt attempt = quizAttemptRepository.findByIdWithAnswers(attemptId)
                .orElseThrow(() -> new NoSuchElementException("Attempt not found"));
        validateAttemptOwner(attempt, userId);
        return QuizMapper.toQuizResultResponseDto(attempt);
    }

    @Override
    @Transactional(readOnly = true)
    public QuizHistoryPageResponseDto getQuizHistory(int page, int size) {
        UUID userId = getCurrentUserId();
        int safePage = Math.max(page, 0);
        int safeSize = size > 0 ? size : 10;
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by("startTime").descending());
        Page<QuizAttempt> attemptPage = quizAttemptRepository.findByUserId(userId, pageable);
        QuizHistoryPageResponseDto dto = QuizMapper.toQuizHistoryPageResponseDto(attemptPage);
        dto.setSummary(buildQuizHistorySummary(userId));
        return dto;
    }

    private QuizHistorySummaryDto buildQuizHistorySummary(UUID userId) {
        long finished = quizAttemptRepository.countByUserIdAndStatusIn(userId, List.of("PASSED", "FAILED"));
        Double passRatePercent = null;
        if (finished > 0) {
            long passed = quizAttemptRepository.countByUserIdAndStatus(userId, "PASSED");
            passRatePercent = (100.0d * passed) / finished;
        }
        Double averageScore = quizAttemptRepository.averageSubmittedScore(userId);
        Number secSum = quizAttemptRepository.sumDurationSecondsByUser(userId);
        long totalSecs = secSum != null ? secSum.longValue() : 0L;
        long totalMinutes = totalSecs / 60L;
        return QuizHistorySummaryDto.builder()
                .passRatePercent(passRatePercent)
                .averageScore(averageScore)
                .totalTimeSpentMinutes(totalMinutes)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public QuizPreviewResponseDto getQuizPreview(UUID quizId) {
        UUID userId = getCurrentUserId();
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new NoSuchElementException("Quiz not found"));

        List<Object[]> countRows = quizQuestionRepository.countQuestionsGroupedByQuizId(List.of(quizId));
        long totalQuestions = countRows.isEmpty()
                ? 0L
                : DocumentMapper.toQuizQuestionCountMap(countRows).getOrDefault(quizId, 0L);

        List<QuizHistoryItemDto> recentAttempts = quizAttemptRepository
                .findByUserIdAndQuizIdOrderByAttemptNumberDesc(userId, quizId)
                .stream()
                .limit(PREVIEW_HISTORY_LIMIT)
                .map(QuizMapper::toQuizHistoryItemDto)
                .collect(Collectors.toList());

        List<QuizQuestion> questionEntities = quizQuestionRepository.findAllByQuizIdWithOptions(quizId);

        return QuizPreviewResponseDto.builder()
                .quizId(quiz.getId() != null ? quiz.getId().toString() : null)
                .quizTitle(quiz.getTitle())
                .description(quiz.getDescription())
                .totalQuestions(totalQuestions)
                .duration(quiz.getDurationMinutes())
                .passScorePercent(quiz.getPassScorePercent())
                .recentAttempts(recentAttempts)
                .questions(QuizMapper.toPreviewQuestionDtos(questionEntities))
                .build();
    }

    private Map<UUID, QuizQuestionOption> resolveSelectedOptions(List<QuizQuestion> questions, SubmitQuizRequestDto request) {
        Map<UUID, QuizQuestion> questionMap = questions.stream()
                .filter(Objects::nonNull)
                .filter(q -> q.getId() != null)
                .collect(Collectors.toMap(QuizQuestion::getId, q -> q));

        Map<UUID, QuizQuestionOption> selectedByQuestionId = new HashMap<>();
        HashSet<UUID> submittedQuestionIds = new HashSet<>();
        if (request == null || request.getAnswers() == null) {
            return selectedByQuestionId;
        }

        for (SubmitQuizAnswerRequestDto answer : request.getAnswers()) {
            if (answer == null) {
                continue;
            }
            String questionIdRaw = answer.getQuestionId();
            if (questionIdRaw == null || questionIdRaw.isBlank()) {
                continue;
            }
            UUID questionId = parseUuid(questionIdRaw, "Invalid questionId");
            QuizQuestion question = questionMap.get(questionId);
            if (question == null) {
                throw new IllegalArgumentException("Question does not belong to quiz");
            }
            if (!submittedQuestionIds.add(questionId)) {
                throw new IllegalArgumentException("Duplicate questionId in answers");
            }

            String optionIdRaw = answer.getSelectedOptionId();
            if (optionIdRaw == null || optionIdRaw.isBlank()) {
                continue;
            }
            UUID selectedOptionId = parseUuid(optionIdRaw, "Invalid selectedOptionId");

            QuizQuestionOption selectedOption = question.getOptions().stream()
                    .filter(Objects::nonNull)
                    .filter(o -> Objects.equals(o.getId(), selectedOptionId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Selected option does not belong to question"));
            selectedByQuestionId.put(questionId, selectedOption);
        }
        return selectedByQuestionId;
    }

    private QuizAttempt findLatestInProgressAttemptForStart(UUID userId, UUID quizId) {
        List<QuizAttempt> inProgressAttempts = quizAttemptRepository.findInProgressAttemptsForUpdate(userId, quizId);
        if (inProgressAttempts == null || inProgressAttempts.isEmpty()) {
            return null;
        }
        return inProgressAttempts.get(0);
    }

    private StartQuizResponseDto toStartQuizResponseDto(QuizAttempt attempt, Quiz quiz) {
        return StartQuizResponseDto.builder()
                .attemptId(attempt.getId().toString())
                .startTime(attempt.getStartTime())
                .durationMinutes(quiz.getDurationMinutes())
                .build();
    }

    private void validateAttemptOwner(QuizAttempt attempt, UUID userId) {
        if (!Objects.equals(attempt.getUserId(), userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Forbidden");
        }
    }

    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetailsImpl userDetails)) {
            throw new IllegalArgumentException("Unauthorized");
        }
        return userDetails.getUser().getId();
    }

    private UUID parseUuid(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(message);
        }
    }
}
