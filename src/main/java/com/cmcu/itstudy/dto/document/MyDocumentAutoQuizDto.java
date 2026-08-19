package com.cmcu.itstudy.dto.document;

import com.cmcu.itstudy.enums.QuizGenerationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyDocumentAutoQuizDto {

    private String documentId;
    private String generationId;
    private QuizGenerationStatus status;
    private Integer requestedQuestionCount;
    private LocalDateTime requestedAt;
    private LocalDateTime processingAt;
    private LocalDateTime readyAt;
    private LocalDateTime failedAt;
    private LocalDateTime cancelledAt;
    private String lastError;
    private Integer attempts;
    private QuizInfo quiz;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuizInfo {
        private String quizId;
        private String title;
        private String description;
        private Long totalQuestions;
        private Integer durationMinutes;
        private Double passScorePercent;
    }
}
