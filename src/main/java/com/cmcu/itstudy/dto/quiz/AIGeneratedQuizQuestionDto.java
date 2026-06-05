package com.cmcu.itstudy.dto.quiz;

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
public class AIGeneratedQuizQuestionDto {

    private String questionText;

    private String explanation;

    private Integer points;

    private List<AIGeneratedQuizOptionDto> options;
}
