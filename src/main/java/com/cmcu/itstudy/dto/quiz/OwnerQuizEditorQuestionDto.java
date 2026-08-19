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
public class OwnerQuizEditorQuestionDto {

    /** May be {@code null} for questions added in the editor that have not been persisted yet. */
    private String questionId;
    private String questionText;
    private String explanation;
    private Integer points;
    private Integer sortOrder;
    private List<OwnerQuizEditorOptionDto> options;
}