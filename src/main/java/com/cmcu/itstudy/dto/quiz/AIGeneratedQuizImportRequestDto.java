package com.cmcu.itstudy.dto.quiz;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIGeneratedQuizImportRequestDto {

    @NotBlank(message = "Quiz title is required")
    private String quizTitle;

    private String quizDescription;

    @NotNull(message = "Document ID is required")
    private UUID documentId;

    @NotEmpty(message = "At least one question is required")
    @Valid
    private List<@Valid AIGeneratedQuizQuestionDto> questions;
}
