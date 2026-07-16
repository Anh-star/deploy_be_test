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
public class PaymentModeratorWithdrawalActionResponseDto {

    private UUID id;
    private String requestCode;

    private UUID sellerId;
    private Long amount;
    private WithdrawalStatus status;

    private String adminNote;

    private UUID approvedByAdminId;
    private UUID rejectedByAdminId;

    private LocalDateTime approvedAt;
    private LocalDateTime rejectedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}