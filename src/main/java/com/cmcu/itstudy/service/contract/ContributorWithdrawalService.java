package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.dto.contributor.ContributorWithdrawalCreateRequestDto;
import com.cmcu.itstudy.dto.contributor.withdrawal.ContributorWithdrawalDetailResponseDto;
import com.cmcu.itstudy.dto.contributor.withdrawal.ContributorWithdrawalPageResponseDto;
import com.cmcu.itstudy.enums.WithdrawalStatus;
import com.cmcu.itstudy.service.dto.ContributorWithdrawalCreateResult;

import java.util.UUID;

public interface ContributorWithdrawalService {

    ContributorWithdrawalCreateResult createWithdrawal(
            UUID sellerId,
            ContributorWithdrawalCreateRequestDto createRequest
    );

    ContributorWithdrawalPageResponseDto getWithdrawalHistory(
            UUID sellerId,
            int page,
            int size,
            WithdrawalStatus status
    );

    ContributorWithdrawalDetailResponseDto getWithdrawalDetail(
            UUID sellerId,
            UUID withdrawalId
    );
}
