package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.dto.quiz.OwnerQuizEditorOptionDto;
import com.cmcu.itstudy.dto.quiz.OwnerQuizEditorQuestionDto;
import com.cmcu.itstudy.dto.quiz.OwnerQuizEditorRequestDto;
import com.cmcu.itstudy.dto.quiz.OwnerQuizEditorResponseDto;
import com.cmcu.itstudy.entity.Quiz;
import com.cmcu.itstudy.entity.QuizAttempt;
import com.cmcu.itstudy.entity.QuizGeneration;
import com.cmcu.itstudy.entity.QuizQuestion;
import com.cmcu.itstudy.entity.QuizQuestionOption;
import com.cmcu.itstudy.repository.QuizAttemptRepository;
import com.cmcu.itstudy.repository.QuizGenerationRepository;
import com.cmcu.itstudy.repository.QuizQuestionRepository;
import com.cmcu.itstudy.repository.QuizRepository;
import com.cmcu.itstudy.security.UserDetailsImpl;
import com.cmcu.itstudy.service.contract.OwnerQuizEditorService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OwnerQuizEditorServiceImpl implements OwnerQuizEditorService {

    private static final int MAX_OPTIONS_PER_QUESTION = 12;

    private final QuizRepository quizRepository;
    private final QuizGenerationRepository quizGenerationRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizAttemptRepository quizAttemptRepository;

    public OwnerQuizEditorServiceImpl(QuizRepository quizRepository,
                                      QuizGenerationRepository quizGenerationRepository,
                                      QuizQuestionRepository quizQuestionRepository,
                                      QuizAttemptRepository quizAttemptRepository) {
        this.quizRepository = quizRepository;
        this.quizGenerationRepository = quizGenerationRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.quizAttemptRepository = quizAttemptRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public OwnerQuizEditorResponseDto getOwnerQuizEditor(UUID quizId) {
        UUID userId = getCurrentUserId();
        Quiz quiz = loadOwnerQuiz(quizId, userId);

        List<QuizQuestion> questions = quizQuestionRepository.findAllByQuizIdWithOptions(quiz.getId());
        questions.sort(Comparator.comparing(QuizQuestion::getSortOrder,
                Comparator.nullsLast(Integer::compareTo)));

        boolean hasAttempts = quizAttemptRepository.existsByQuiz_Id(quiz.getId());

        return OwnerQuizEditorResponseDto.builder()
                .quizId(uuidToString(quiz.getId()))
                .title(quiz.getTitle())
                .description(quiz.getDescription())
                .durationMinutes(quiz.getDurationMinutes())
                .passScorePercent(quiz.getPassScorePercent())
                .hasAttempts(hasAttempts)
                .questions(toQuestionDtos(questions))
                .build();
    }

    @Override
    @Transactional
    public OwnerQuizEditorResponseDto saveOwnerQuizEditor(UUID quizId, OwnerQuizEditorRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }

        UUID userId = getCurrentUserId();
        Quiz quiz = loadOwnerQuiz(quizId, userId);

        boolean hasAttempts = quizAttemptRepository.existsByQuiz_Id(quiz.getId());

        validatePayloadSemantics(request);

        List<QuizQuestion> existingQuestions = quizQuestionRepository.findAllByQuizIdWithOptions(quiz.getId());
        Map<UUID, QuizQuestion> existingById = existingQuestions.stream()
                .filter(q -> q.getId() != null)
                .collect(Collectors.toMap(QuizQuestion::getId, q -> q));

        Set<UUID> keepQuestionIds = new HashSet<>();
        List<QuizQuestion> orderedQuestions = new ArrayList<>();

        for (OwnerQuizEditorRequestDto.OwnerQuizEditorQuestionPayloadDto qPayload : request.getQuestions()) {
            QuizQuestion question;
            if (qPayload.getQuestionId() != null && !qPayload.getQuestionId().isBlank()) {
                UUID qid = parseUuid(qPayload.getQuestionId(), "Invalid questionId");
                QuizQuestion existing = existingById.get(qid);
                if (existing == null) {
                    throw new IllegalArgumentException("Question does not belong to this quiz");
                }
                question = existing;
                keepQuestionIds.add(qid);
            } else {
                question = QuizQuestion.builder()
                        .quiz(quiz)
                        .options(new ArrayList<>())
                        .build();
            }

            question.setQuestionText(qPayload.getQuestionText());
            question.setExplanation(qPayload.getExplanation());
            question.setPoints(qPayload.getPoints());
            question.setSortOrder(qPayload.getSortOrder());

            List<QuizQuestionOption> existingOptions = question.getOptions();
            Map<UUID, QuizQuestionOption> existingOptsById = existingOptions.stream()
                    .filter(o -> o.getId() != null)
                    .collect(Collectors.toMap(QuizQuestionOption::getId, o -> o));

            Set<UUID> keepOptionIds = new HashSet<>();
            List<QuizQuestionOption> orderedOptions = new ArrayList<>();
            for (OwnerQuizEditorRequestDto.OwnerQuizEditorQuestionPayloadDto.OwnerQuizEditorOptionPayloadDto oPayload
                    : qPayload.getOptions()) {
                QuizQuestionOption option;
                if (oPayload.getOptionId() != null && !oPayload.getOptionId().isBlank()) {
                    UUID oid = parseUuid(oPayload.getOptionId(), "Invalid optionId");
                    QuizQuestionOption existingOpt = existingOptsById.get(oid);
                    if (existingOpt == null) {
                        throw new IllegalArgumentException("Option does not belong to this question");
                    }
                    option = existingOpt;
                    keepOptionIds.add(oid);
                } else {
                    option = QuizQuestionOption.builder().question(question).build();
                }
                option.setContent(oPayload.getContent());
                option.setIsCorrect(oPayload.isCorrect());
                option.setSortOrder(oPayload.getSortOrder());
                orderedOptions.add(option);
            }

            if (hasAttempts) {
                for (UUID existingOptId : existingOptsById.keySet()) {
                    if (!keepOptionIds.contains(existingOptId)) {
                        throw new IllegalStateException(
                                "Cannot delete existing options while quiz has attempts");
                    }
                }
            } else {
                existingOptsById.keySet().retainAll(keepOptionIds);
            }

            question.getOptions().clear();
            question.getOptions().addAll(orderedOptions);
            orderedQuestions.add(question);
        }

        if (hasAttempts) {
            for (UUID existingQId : existingById.keySet()) {
                if (!keepQuestionIds.contains(existingQId)) {
                    throw new IllegalStateException(
                            "Cannot delete existing questions while quiz has attempts");
                }
            }
        }

        quiz.setTitle(request.getTitle());
        quiz.setDescription(request.getDescription());
        quiz.setDurationMinutes(request.getDurationMinutes());
        quiz.setPassScorePercent(request.getPassScorePercent());

        for (QuizQuestion orphan : existingQuestions) {
            if (orphan.getId() != null && !keepQuestionIds.contains(orphan.getId())) {
                if (!hasAttempts) {
                    quizQuestionRepository.delete(orphan);
                }
            }
        }

        List<QuizQuestion> persistedQuestions = new ArrayList<>();
        for (QuizQuestion q : orderedQuestions) {
            persistedQuestions.add(quizQuestionRepository.save(q));
        }
        quiz.getQuestions().clear();
        quiz.getQuestions().addAll(persistedQuestions);
        Quiz savedQuiz = quizRepository.save(quiz);

        List<QuizQuestion> reload = quizQuestionRepository.findAllByQuizIdWithOptions(savedQuiz.getId());
        reload.sort(Comparator.comparing(QuizQuestion::getSortOrder,
                Comparator.nullsLast(Integer::compareTo)));

        return OwnerQuizEditorResponseDto.builder()
                .quizId(uuidToString(savedQuiz.getId()))
                .title(savedQuiz.getTitle())
                .description(savedQuiz.getDescription())
                .durationMinutes(savedQuiz.getDurationMinutes())
                .passScorePercent(savedQuiz.getPassScorePercent())
                .hasAttempts(hasAttempts)
                .questions(toQuestionDtos(reload))
                .build();
    }

    private Quiz loadOwnerQuiz(UUID quizId, UUID userId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new NoSuchElementException("Quiz not found"));

        QuizGeneration generation = quizGenerationRepository.findByQuiz_Id(quiz.getId())
                .orElseThrow(() -> new NoSuchElementException("Quiz not found"));

        if (generation.getDocument() == null
                || generation.getDocument().getCreatedBy() == null
                || generation.getDocument().getCreatedBy().getId() == null) {
            throw new NoSuchElementException("Quiz not found");
        }

        UUID ownerId = generation.getDocument().getCreatedBy().getId();
        if (!Objects.equals(ownerId, userId)) {
            throw new AccessDeniedException("You do not have permission to edit this quiz");
        }
        return quiz;
    }

    private void validatePayloadSemantics(OwnerQuizEditorRequestDto request) {
        List<OwnerQuizEditorRequestDto.OwnerQuizEditorQuestionPayloadDto> questions = request.getQuestions();
        if (questions == null || questions.isEmpty()) {
            throw new IllegalArgumentException("Quiz must have at least one question");
        }

        Set<String> seenQuestionIds = new HashSet<>();
        Map<String, Set<String>> seenOptionIdsPerQuestion = new HashMap<>();

        int qIndex = 0;
        for (OwnerQuizEditorRequestDto.OwnerQuizEditorQuestionPayloadDto q : questions) {
            if (q == null) {
                throw new IllegalArgumentException("Question at index " + qIndex + " is null");
            }
            if (q.getQuestionText() == null || q.getQuestionText().isBlank()) {
                throw new IllegalArgumentException("Question " + qIndex + " text must not be blank");
            }
            if (q.getPoints() == null || q.getPoints() < 1) {
                throw new IllegalArgumentException("Question " + qIndex + " points must be >= 1");
            }
            if (q.getSortOrder() == null || q.getSortOrder() < 1) {
                throw new IllegalArgumentException("Question " + qIndex + " sortOrder must be >= 1");
            }
            if (q.getQuestionId() != null && !q.getQuestionId().isBlank()) {
                if (!seenQuestionIds.add(q.getQuestionId())) {
                    throw new IllegalArgumentException("Duplicate questionId in payload");
                }
            }

            List<OwnerQuizEditorRequestDto.OwnerQuizEditorQuestionPayloadDto.OwnerQuizEditorOptionPayloadDto> options = q.getOptions();
            if (options == null || options.size() < 2) {
                throw new IllegalArgumentException("Question " + qIndex + " must have at least 2 options");
            }
            if (options.size() > MAX_OPTIONS_PER_QUESTION) {
                throw new IllegalArgumentException("Question " + qIndex + " exceeds maximum options");
            }

            int correctCount = 0;
            Set<String> seenOptionIds = seenOptionIdsPerQuestion.computeIfAbsent(
                    String.valueOf(qIndex), k -> new HashSet<>());

            int oIndex = 0;
            for (OwnerQuizEditorRequestDto.OwnerQuizEditorQuestionPayloadDto.OwnerQuizEditorOptionPayloadDto o : options) {
                if (o == null) {
                    throw new IllegalArgumentException("Question " + qIndex + " option " + oIndex + " is null");
                }
                if (o.getContent() == null || o.getContent().isBlank()) {
                    throw new IllegalArgumentException("Question " + qIndex + " option " + oIndex + " content must not be blank");
                }
                if (o.getSortOrder() == null || o.getSortOrder() < 1) {
                    throw new IllegalArgumentException("Question " + qIndex + " option " + oIndex + " sortOrder must be >= 1");
                }
                if (o.isCorrect()) {
                    correctCount++;
                }
                if (o.getOptionId() != null && !o.getOptionId().isBlank()) {
                    if (!seenOptionIds.add(o.getOptionId())) {
                        throw new IllegalArgumentException("Duplicate optionId in payload for question " + qIndex);
                    }
                }
                oIndex++;
            }

            if (correctCount != 1) {
                throw new IllegalArgumentException("Question " + qIndex + " must have exactly one correct option");
            }
            qIndex++;
        }
    }

    private List<OwnerQuizEditorQuestionDto> toQuestionDtos(List<QuizQuestion> questions) {
        List<OwnerQuizEditorQuestionDto> out = new ArrayList<>();
        for (QuizQuestion q : questions) {
            if (q == null) {
                continue;
            }
            List<OwnerQuizEditorOptionDto> options = new ArrayList<>();
            if (q.getOptions() != null) {
                List<QuizQuestionOption> sortedOpts = new ArrayList<>(q.getOptions());
                sortedOpts.sort(Comparator.comparing(QuizQuestionOption::getSortOrder,
                        Comparator.nullsLast(Integer::compareTo)));
                for (QuizQuestionOption o : sortedOpts) {
                    if (o == null) continue;
                    options.add(OwnerQuizEditorOptionDto.builder()
                            .optionId(uuidToString(o.getId()))
                            .content(o.getContent())
                            .isCorrect(Boolean.TRUE.equals(o.getIsCorrect()))
                            .sortOrder(o.getSortOrder())
                            .build());
                }
            }
            out.add(OwnerQuizEditorQuestionDto.builder()
                    .questionId(uuidToString(q.getId()))
                    .questionText(q.getQuestionText())
                    .explanation(q.getExplanation())
                    .points(q.getPoints())
                    .sortOrder(q.getSortOrder())
                    .options(options)
                    .build());
        }
        return out;
    }

    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetailsImpl userDetails)) {
            throw new IllegalArgumentException("Unauthorized");
        }
        return userDetails.getUser().getId();
    }

    private static UUID parseUuid(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(message);
        }
    }

    private static String uuidToString(UUID id) {
        return id != null ? id.toString() : null;
    }
}