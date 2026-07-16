package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.dto.paymentmoderator.withdrawal.PaymentModeratorWithdrawalDetailResponseDto;
import com.cmcu.itstudy.dto.paymentmoderator.withdrawal.PaymentModeratorWithdrawalPageResponseDto;
import com.cmcu.itstudy.enums.WithdrawalStatus;

import java.util.UUID;

public interface PaymentModeratorWithdrawalQueryService {

    PaymentModeratorWithdrawalPageResponseDto listWithdrawals(
            int page,
            int size,
            WithdrawalStatus status,
            String search
    );

    PaymentModeratorWithdrawalDetailResponseDto getWithdrawal(UUID withdrawalId);
}
