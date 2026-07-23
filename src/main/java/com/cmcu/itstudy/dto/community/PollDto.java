package com.cmcu.itstudy.dto.community;

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
public class PollDto {
    private String id;
    private String question;
    private LocalDateTime expiresAt;
    private Boolean allowMultiple;
    private Integer totalVotes;
    private List<PollOptionDto> options;
}
