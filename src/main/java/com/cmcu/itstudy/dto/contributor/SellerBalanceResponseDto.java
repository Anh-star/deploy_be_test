package com.cmcu.itstudy.dto.contributor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerBalanceResponseDto {

    private Long pendingBalance;
    private Long availableBalance;
    private Long lockedBalance;
    private Long totalEarned;
    private Long totalWithdrawn;
}