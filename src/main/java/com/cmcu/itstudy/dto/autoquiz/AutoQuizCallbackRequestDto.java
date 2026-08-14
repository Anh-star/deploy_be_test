package com.cmcu.itstudy.dto.autoquiz;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * Phase 2E callback request DTO from n8n to the backend.
 *
 * <p>This DTO is the machine-to-machine contract. n8n sends the
 * generated questions and the backend persists them as a {@code Quiz}
 * linked to the {@code QuizGeneration} identified by the
 * {@code generationId} path parameter.
 *
 * <p>Security: the endpoint is authenticated by the
 * {@code X-Auto-Quiz-Dispatch-Token} header which must exactly
 * match the {@code dispatchToken} stored on the {@code QuizGeneration}
 * row. The {@code generationId} in the URL path is the only external
 * identifier used to resolve the generation row.
 *
 * <h3>Validation summary</h3>
 * <ul>
 *   <li>{@code requestedQuestionCount} &mdash; integer 10&ndash;50</li>
 *   <li>{@code questions} &mdash; non-null array,
 *       length == {@code requestedQuestionCount},
 *       length == {@code QuizGeneration.requestedQuestionCount}</li>
 *   <li>Each question: non-blank text, exactly 4 non-blank answers,
 *       {@code correct} integer 0&ndash;3</li>
 * </ul>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoQuizCallbackRequestDto {

    @NotNull(message = "requestedQuestionCount is required")
    @Min(value = 10, message = "requestedQuestionCount must be at least 10")
    @Max(value = 50, message = "requestedQuestionCount must be at most 50")
    private Integer requestedQuestionCount;

    @NotNull(message = "questions is required")
    @NotEmpty(message = "questions must not be empty")
    @Valid
    private List<@Valid AutoQuizCallbackQuestionDto> questions;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AutoQuizCallbackQuestionDto {

        @NotBlank(message = "question text is required")
        @Size(max = 4000, message = "question text must not exceed 4000 characters")
        private String question;

        @NotNull(message = "answers are required")
        @NotEmpty(message = "answers must not be empty")
        private List<@NotBlank(message = "each answer must be non-blank")
                      String> answers;

        @NotNull(message = "correct is required")
        @Min(value = 0, message = "correct must be 0, 1, 2, or 3")
        @Max(value = 3, message = "correct must be 0, 1, 2, or 3")
        private Integer correct;
    }
}
