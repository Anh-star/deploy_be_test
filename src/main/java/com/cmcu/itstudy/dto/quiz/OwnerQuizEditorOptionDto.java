package com.cmcu.itstudy.dto.quiz;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerQuizEditorOptionDto {

    /** May be {@code null} for options added in the editor that have not been persisted yet. */
    private String optionId;
    private String content;

    /**
     * Whether this option is the correct answer for its question. Pinned
     * to the {@code isCorrect} JSON key by {@link JsonProperty} so the
     * wire contract is stable regardless of Lombok / Jackson field-name
     * heuristics.
     */
    @JsonProperty("isCorrect")
    private boolean isCorrect;

    private Integer sortOrder;
}