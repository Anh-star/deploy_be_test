package com.cmcu.itstudy.controller;

import com.cmcu.itstudy.dto.common.ApiResponse;
import com.cmcu.itstudy.dto.quiz.AIGeneratedQuizImportRequestDto;
import com.cmcu.itstudy.dto.quiz.AIGeneratedQuizImportResponseDto;
import com.cmcu.itstudy.service.contract.QuizImportService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quizzes")
public class QuizImportController {

    private final QuizImportService quizImportService;

    public QuizImportController(QuizImportService quizImportService) {
        this.quizImportService = quizImportService;
    }

    @PostMapping("/ai-import")
    public ResponseEntity<ApiResponse<AIGeneratedQuizImportResponseDto>> importAIGeneratedQuiz(
            @Valid @RequestBody AIGeneratedQuizImportRequestDto request) {
        AIGeneratedQuizImportResponseDto response = quizImportService.importAIGeneratedQuiz(request);
        return ResponseEntity.ok(ApiResponse.success(response, "AI-generated quiz imported successfully"));
    }
}
