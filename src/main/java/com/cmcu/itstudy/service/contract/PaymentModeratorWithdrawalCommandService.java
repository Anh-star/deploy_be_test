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

    PaymentModeratorWithdrawalActionResponseDto markPaid(
            UUID withdrawalId,
            UUID moderatorId,
            String adminNote
    );

    /**
     * One-step success path for new requests:
     * PENDING -> PAID atomically. Moves locked -> withdrawn and stamps
     * both approvedAt and paidAt with the same {@code now} value.
     *
     * <p>Legacy APPROVED requests continue to use {@link #markPaid}.</p>
     */
    PaymentModeratorWithdrawalActionResponseDto approveAndMarkPaid(
            UUID withdrawalId,
            UUID moderatorId,
            String adminNote
    );
}