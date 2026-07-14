package com.cmcu.itstudy.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerEarningBatchReleaseResult {

    private int scannedCount;
    private int releasedCount;
    private int skippedCount;
    private int failedCount;
}