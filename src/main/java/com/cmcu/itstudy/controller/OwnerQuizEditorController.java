package com.cmcu.itstudy.controller;

import com.cmcu.itstudy.dto.common.ApiResponse;
import com.cmcu.itstudy.dto.quiz.OwnerQuizEditorRequestDto;
import com.cmcu.itstudy.dto.quiz.OwnerQuizEditorResponseDto;
import com.cmcu.itstudy.service.contract.OwnerQuizEditorService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Owner-only quiz editor endpoints.
 *
 * <p>Mounted at {@code /api/my-quizzes} so it inherits the default
 * {@code authenticated()} rule in
 * {@link com.cmcu.itstudy.config.SecurityConfig}. Owner authorization is
 * enforced inside the service via
 * {@code QuizGeneration.document.createdBy == currentUser}.</p>
 */
@RestController
@RequestMapping("/api/my-quizzes")
public class OwnerQuizEditorController {

    private final OwnerQuizEditorService ownerQuizEditorService;

    public OwnerQuizEditorController(OwnerQuizEditorService ownerQuizEditorService) {
        this.ownerQuizEditorService = ownerQuizEditorService;
    }

    @GetMapping("/{quizId}/editor")
    public ResponseEntity<ApiResponse<OwnerQuizEditorResponseDto>> getOwnerQuizEditor(
            @PathVariable("quizId") UUID quizId) {
        OwnerQuizEditorResponseDto data = ownerQuizEditorService.getOwnerQuizEditor(quizId);
        return ResponseEntity.ok(ApiResponse.success(data, "Owner quiz editor"));
    }

    @PutMapping("/{quizId}/editor")
    public ResponseEntity<ApiResponse<OwnerQuizEditorResponseDto>> saveOwnerQuizEditor(
            @PathVariable("quizId") UUID quizId,
            @Valid @RequestBody OwnerQuizEditorRequestDto request) {
        OwnerQuizEditorResponseDto data = ownerQuizEditorService.saveOwnerQuizEditor(quizId, request);
        return ResponseEntity.ok(ApiResponse.success(data, "Đã lưu bài đánh giá"));
    }
}