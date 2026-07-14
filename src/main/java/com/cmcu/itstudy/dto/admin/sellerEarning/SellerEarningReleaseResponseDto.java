package com.cmcu.itstudy.dto.admin.sellerEarning;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerEarningReleaseResponseDto {

    private UUID earningId;
    private boolean released;
    private String message;
}