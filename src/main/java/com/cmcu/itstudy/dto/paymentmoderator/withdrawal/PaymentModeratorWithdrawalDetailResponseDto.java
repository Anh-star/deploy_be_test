package com.cmcu.itstudy.dto.paymentmoderator.withdrawal;

import com.cmcu.itstudy.enums.WithdrawalStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentModeratorWithdrawalDetailResponseDto {

    private UUID id;
    private String requestCode;
    private UUID clientRequestId;

    private UUID sellerId;
    private String sellerEmail;
    private String sellerFullName;

    private Long amount;
    private WithdrawalStatus status;

    private String bankCode;
    private String bankName;
    private String bankAccountNumber;
    private String bankAccountHolderName;

    private String sellerNote;
    private String adminNote;

    private UUID approvedByAdminId;
    private UUID paidByAdminId;
    private UUID rejectedByAdminId;

    private LocalDateTime approvedAt;
    private LocalDateTime paidAt;
    private LocalDateTime rejectedAt;
    private LocalDateTime cancelledAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
