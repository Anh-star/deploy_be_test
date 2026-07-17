package com.cmcu.itstudy.dto.contributor.withdrawal;

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
public class ContributorWithdrawalHistoryItemDto {

    private UUID id;
    private String requestCode;
    private Long amount;
    private WithdrawalStatus status;
    private String bankCode;
    private String bankName;
    private String maskedBankAccountNumber;
    private String bankAccountHolderName;
    private String sellerNote;
    private String rejectionReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime paidAt;
    private LocalDateTime rejectedAt;
    private LocalDateTime cancelledAt;
}
