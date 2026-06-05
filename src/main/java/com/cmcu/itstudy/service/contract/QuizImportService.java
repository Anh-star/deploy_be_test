package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.dto.quiz.AIGeneratedQuizImportRequestDto;
import com.cmcu.itstudy.dto.quiz.AIGeneratedQuizImportResponseDto;

public interface QuizImportService {

    AIGeneratedQuizImportResponseDto importAIGeneratedQuiz(AIGeneratedQuizImportRequestDto request);
}
