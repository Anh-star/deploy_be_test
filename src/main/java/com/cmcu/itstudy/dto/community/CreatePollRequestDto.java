package com.cmcu.itstudy.dto.community;

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
public class CreatePollRequestDto {
    private String question;
    private List<String> options;
    private List<UpdatePollOptionDto> pollOptions;
    private Integer durationDays;
    private Boolean allowMultiple;
    private Boolean allowAddOptions;
    private Boolean hideResultsBeforeVote;
    private Boolean hideVoters;
}
