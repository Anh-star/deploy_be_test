package com.cmcu.itstudy.dto.quiz;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Owner-only response payload for the quiz editor screen.
 *
 * <p>Exposes {@code isCorrect} on every option because only the document
 * owner is allowed to call this endpoint. Public preview endpoints MUST
 * NOT use this DTO.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerQuizEditorResponseDto {

    private String quizId;
    private String title;
    private String description;
    private Integer durationMinutes;
    private Double passScorePercent;
    private boolean hasAttempts;
    private List<OwnerQuizEditorQuestionDto> questions;
}