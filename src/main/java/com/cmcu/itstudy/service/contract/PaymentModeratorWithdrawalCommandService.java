package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.dto.paymentmoderator.withdrawal.PaymentModeratorWithdrawalActionResponseDto;

import java.util.UUID;

public interface PaymentModeratorWithdrawalCommandService {

    PaymentModeratorWithdrawalActionResponseDto approveWithdrawal(
            UUID withdrawalId,
            UUID moderatorId,
            String adminNote
    );

    PaymentModeratorWithdrawalActionResponseDto rejectWithdrawal(
            UUID withdrawalId,
            UUID moderatorId,
            String adminNote
    );
}