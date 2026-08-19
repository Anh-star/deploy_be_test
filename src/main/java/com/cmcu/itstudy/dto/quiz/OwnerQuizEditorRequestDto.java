package com.cmcu.itstudy.dto.quiz;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerQuizEditorRequestDto {

    @NotBlank
    @Size(max = 255)
    private String title;

    private String description;

    @NotNull
    @Min(1)
    private Integer durationMinutes;

    @NotNull
    @Min(0)
    private Double passScorePercent;

    @NotNull
    @Size(min = 1, message = "Quiz must have at least one question")
    @Valid
    private List<OwnerQuizEditorQuestionPayloadDto> questions;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OwnerQuizEditorQuestionPayloadDto {

        /** May be {@code null} for new questions. */
        private String questionId;

        @NotBlank
        private String questionText;

        private String explanation;

        @NotNull
        @Min(1)
        private Integer points;

        @NotNull
        private Integer sortOrder;

        @NotNull
        @Size(min = 2, message = "Each question must have at least two options")
        @Valid
        private List<OwnerQuizEditorOptionPayloadDto> options;

        @Getter
        @Setter
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class OwnerQuizEditorOptionPayloadDto {

            /** May be {@code null} for new options. */
            private String optionId;

            @NotBlank
            private String content;

            /**
             * Canonical wire key is {@code isCorrect}. {@code correct} is
             * accepted as a legacy alias so older FE clients keep working.
             */
            @JsonProperty("isCorrect")
            @JsonAlias({"correct"})
            private boolean isCorrect;

            @NotNull
            private Integer sortOrder;
        }
    }
}