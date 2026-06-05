package com.cmcu.itstudy.mapper;

import com.cmcu.itstudy.dto.quiz.AIGeneratedQuizImportResponseDto;
import com.cmcu.itstudy.entity.Quiz;

import java.util.UUID;

public final class QuizImportMapper {

    private QuizImportMapper() {
    }

    public static AIGeneratedQuizImportResponseDto toResponseDto(Quiz quiz, int questionsImported) {
        if (quiz == null) {
            return null;
        }
        return AIGeneratedQuizImportResponseDto.builder()
                .quizId(quiz.getId())
                .quizTitle(quiz.getTitle())
                .questionsImported(questionsImported)
                .message("Quiz imported successfully")
                .build();
    }

    public static String uuidToString(UUID id) {
        return id != null ? id.toString() : null;
    }
}
