package com.cmcu.itstudy.dto.community;

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
public class PollOptionDto {
    private String id;
    private String optionText;
    private Integer voteCount;
    private Boolean isVotedByCurrentUser;
}
