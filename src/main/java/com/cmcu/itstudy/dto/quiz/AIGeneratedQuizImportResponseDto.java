package com.cmcu.itstudy.dto.quiz;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIGeneratedQuizImportResponseDto {

    private UUID quizId;

    private String quizTitle;

    private int questionsImported;

    private String message;
}
