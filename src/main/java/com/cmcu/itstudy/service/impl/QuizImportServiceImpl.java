package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.dto.quiz.AIGeneratedQuizImportRequestDto;
import com.cmcu.itstudy.dto.quiz.AIGeneratedQuizImportResponseDto;
import com.cmcu.itstudy.dto.quiz.AIGeneratedQuizOptionDto;
import com.cmcu.itstudy.dto.quiz.AIGeneratedQuizQuestionDto;
import com.cmcu.itstudy.entity.Document;
import com.cmcu.itstudy.entity.DocumentQuiz;
import com.cmcu.itstudy.entity.Quiz;
import com.cmcu.itstudy.entity.QuizQuestion;
import com.cmcu.itstudy.entity.QuizQuestionOption;
import com.cmcu.itstudy.handle.AIGeneratedQuizValidationException;
import com.cmcu.itstudy.mapper.QuizImportMapper;
import com.cmcu.itstudy.repository.DocumentQuizRepository;
import com.cmcu.itstudy.repository.DocumentRepository;
import com.cmcu.itstudy.repository.QuizQuestionRepository;
import com.cmcu.itstudy.repository.QuizRepository;
import com.cmcu.itstudy.service.contract.QuizImportService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cmcu.itstudy.repository.QuizQuestionOptionRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class QuizImportServiceImpl implements QuizImportService {

    private final QuizRepository quizRepository;
private final QuizQuestionRepository quizQuestionRepository;
private final QuizQuestionOptionRepository quizQuestionOptionRepository;
private final DocumentQuizRepository documentQuizRepository;
private final DocumentRepository documentRepository;
    
    

public QuizImportServiceImpl(
    QuizRepository quizRepository,
    QuizQuestionRepository quizQuestionRepository,
    QuizQuestionOptionRepository quizQuestionOptionRepository,
    DocumentQuizRepository documentQuizRepository,
    DocumentRepository documentRepository
) {
this.quizRepository = quizRepository;
this.quizQuestionRepository = quizQuestionRepository;
this.quizQuestionOptionRepository = quizQuestionOptionRepository;
this.documentQuizRepository = documentQuizRepository;
this.documentRepository = documentRepository;
}

    @Override
    @Transactional
    public AIGeneratedQuizImportResponseDto importAIGeneratedQuiz(AIGeneratedQuizImportRequestDto request) {
        validateRequest(request);

        Document document = documentRepository.findById(request.getDocumentId())
                .orElseThrow(() -> new NoSuchElementException("Document not found with id: " + request.getDocumentId()));

        Quiz quiz = buildQuiz(request);
        Quiz savedQuiz = quizRepository.save(quiz);

        int sortOrder = 0;

for (AIGeneratedQuizQuestionDto questionDto : request.getQuestions()) {

    QuizQuestion question =
            buildQuestion(savedQuiz, questionDto, sortOrder++);

    QuizQuestion savedQuestion =
            quizQuestionRepository.save(question);
            System.out.println(
                "Question saved id = "
                        + savedQuestion.getId()
        );
        
        System.out.println(
                "Options before save = "
                        + question.getOptions().size()
        );

    System.out.println(
            "Saving options count = "
                    + question.getOptions().size()
    );

    for (QuizQuestionOption option : question.getOptions()) {
        option.setQuestion(savedQuestion);
    }

    quizQuestionOptionRepository.saveAll(question.getOptions());
    System.out.println(
        "Options saved successfully"
);
}

        int existingLinks = documentQuizRepository.findAllByDocumentIdWithQuiz(document.getId()).size();
        DocumentQuiz documentQuiz = DocumentQuiz.builder()
                .document(document)
                .quiz(savedQuiz)
                .sortOrder(existingLinks + 1)
                .build();
        documentQuizRepository.save(documentQuiz);

        return QuizImportMapper.toResponseDto(savedQuiz, request.getQuestions().size());
    }

    private void validateRequest(AIGeneratedQuizImportRequestDto request) {
        if (request == null) {
            throw new AIGeneratedQuizValidationException("Request payload cannot be null");
        }
        if (request.getQuizTitle() == null || request.getQuizTitle().isBlank()) {
            throw new AIGeneratedQuizValidationException("Quiz title is required and cannot be blank");
        }
        if (request.getQuestions() == null || request.getQuestions().isEmpty()) {
            throw new AIGeneratedQuizValidationException("At least one question is required");
        }

        for (int i = 0; i < request.getQuestions().size(); i++) {
            AIGeneratedQuizQuestionDto question = request.getQuestions().get(i);
            if (question == null) {
                throw new AIGeneratedQuizValidationException("Question at index " + i + " cannot be null");
            }
            if (question.getQuestionText() == null || question.getQuestionText().isBlank()) {
                throw new AIGeneratedQuizValidationException("Question text is required and cannot be blank for question at index " + i);
            }
            if (question.getOptions() == null || question.getOptions().size() < 2) {
                throw new AIGeneratedQuizValidationException(
                        "Question at index " + i + " must have at least 2 options, found: " +
                        (question.getOptions() == null ? 0 : question.getOptions().size()));
            }

            boolean hasCorrectAnswer = false;
            for (int j = 0; j < question.getOptions().size(); j++) {
                AIGeneratedQuizOptionDto option = question.getOptions().get(j);
                if (option == null) {
                    throw new AIGeneratedQuizValidationException(
                            "Option at index " + j + " cannot be null for question at index " + i);
                }
                if (option.getContent() == null || option.getContent().isBlank()) {
                    throw new AIGeneratedQuizValidationException(
                            "Option content is required and cannot be blank for option at index " + j + " in question at index " + i);
                }
                if (Boolean.TRUE.equals(option.getIsCorrect())) {
                    hasCorrectAnswer = true;
                }
            }
            if (!hasCorrectAnswer) {
                throw new AIGeneratedQuizValidationException(
                        "Question at index " + i + " must have at least one correct answer (isCorrect = true)");
            }
        }
    }

    private Quiz buildQuiz(AIGeneratedQuizImportRequestDto request) {
        return Quiz.builder()
                .title(request.getQuizTitle().trim())
                .description(request.getQuizDescription() != null ? request.getQuizDescription().trim() : null)
                .durationMinutes(30)
                .maxAttemptsPerDay(3)
                .passScorePercent(80.0d)
                .published(true)
                .build();
    }

    private QuizQuestion buildQuestion(Quiz quiz, AIGeneratedQuizQuestionDto dto, int sortOrder) {
        List<QuizQuestionOption> options = new ArrayList<>();
        int optionSortOrder = 0;
        for (AIGeneratedQuizOptionDto optionDto : dto.getOptions()) {
            QuizQuestionOption option = QuizQuestionOption.builder()
                    .question(null)
                    .content(optionDto.getContent().trim())
                    .isCorrect(Boolean.TRUE.equals(optionDto.getIsCorrect()))
                    .sortOrder(optionSortOrder++)
                    .build();
            options.add(option);
        }

        QuizQuestion question = QuizQuestion.builder()
                .quiz(quiz)
                .sortOrder(sortOrder)
                .questionText(dto.getQuestionText().trim())
                .explanation(dto.getExplanation() != null ? dto.getExplanation().trim() : null)
                .points(dto.getPoints() != null && dto.getPoints() > 0 ? dto.getPoints() : 1)
                .options(options)
                .build();

        for (QuizQuestionOption option : options) {
            option.setQuestion(question);
        }

        return question;
    }
}
