package com.cmcu.itstudy.dto.quiz;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizResultResponseDto {

    private Double score;
    private Double maxScore;
    private Double scorePercent;
    private String status;
    private Integer totalQuestions;
    private Integer correctCount;
    private Integer wrongCount;
    private Integer skippedCount;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private List<QuizResultQuestionDto> questions;

    /** Phase 6H — quiz id từ attempt.quiz, dùng cho nút "Làm lại". */
    private String quizId;

    /** Phase 6H — document id thật của quiz (qua DocumentQuiz). Có thể null nếu quiz không gắn document. */
    private String documentId;
}
