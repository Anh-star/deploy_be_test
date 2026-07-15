package com.cmcu.itstudy.dto.contributor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerPayoutProfileResponseDto {

    private boolean configured;
    private String bankCode;
    private String bankName;
    private String maskedBankAccountNumber;
    private String bankAccountHolderName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}