package com.cmcu.itstudy.service.dto;

import com.cmcu.itstudy.dto.contributor.ContributorWithdrawalResponseDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContributorWithdrawalCreateResult {

    private ContributorWithdrawalResponseDto data;

    private boolean created;
}
