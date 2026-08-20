package com.cmcu.itstudy.dto.document;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for creating an additional AI quiz generation for a
 * document the authenticated user owns.
 *
 * <p>Created via
 * {@code POST /api/my-documents/{documentId}/auto-quizzes}.
 * The {@code documentId} comes from the path; this DTO carries only the
 * generation parameters.</p>
 *
 * <p>Validation rules mirror those already enforced by
 * {@link com.cmcu.itstudy.service.contract.QuizGenerationService}
 * {@code enqueueForDocument}: the service re-validates on the server side
 * so a client that bypasses this DTO (or a programmatic caller) still
 * cannot violate the constraints.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MyDocumentAutoQuizCreateRequestDto {

    /**
     * Desired number of questions for the generated quiz.
     * Hard-capped at {@code [10, 50]} by the service.
     */
    @NotNull(message = "Vui lòng chọn số câu hỏi.")
    @Min(value = 10, message = "Số câu hỏi phải từ 10 đến 50.")
    @Max(value = 50, message = "Số câu hỏi phải từ 10 đến 50.")
    private Integer requestedQuestionCount;

    /**
     * Optional owner-supplied focus topic that biases the AI toward a
     * sub-area of the document. {@code null} means "whole document,
     * no focus". Blank strings are normalised to {@code null} by the
     * service. Length is hard-capped at 500 characters; the service
     * throws {@link IllegalArgumentException} on overflow.
     */
    @Size(max = 500, message = "Nội dung trọng tâm không được vượt quá 500 ký tự.")
    private String focusTopic;
}
