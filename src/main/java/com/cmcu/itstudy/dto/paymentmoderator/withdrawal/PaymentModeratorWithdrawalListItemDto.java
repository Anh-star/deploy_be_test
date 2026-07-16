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
public class PaymentModeratorWithdrawalListItemDto {

    private UUID id;
    private String requestCode;

    private UUID sellerId;
    private String sellerEmail;
    private String sellerFullName;

    private Long amount;
    private WithdrawalStatus status;

    private String bankCode;
    private String bankName;
    private String maskedBankAccountNumber;
    private String bankAccountHolderName;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
