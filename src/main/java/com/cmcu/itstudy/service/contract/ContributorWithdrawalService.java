package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.dto.contributor.ContributorWithdrawalCreateRequestDto;
import com.cmcu.itstudy.service.dto.ContributorWithdrawalCreateResult;

import java.util.UUID;

public interface ContributorWithdrawalService {

    ContributorWithdrawalCreateResult createWithdrawal(
            UUID sellerId,
            ContributorWithdrawalCreateRequestDto createRequest
    );
}
