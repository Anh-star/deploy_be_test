package com.cmcu.itstudy.schedule;

import com.cmcu.itstudy.service.contract.SellerEarningBatchReleaseService;
import com.cmcu.itstudy.service.dto.SellerEarningBatchReleaseResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SellerEarningReleaseScheduler {

    private final SellerEarningBatchReleaseService sellerEarningBatchReleaseService;

    @Value("${seller.earning-release.batch-size:20}")
    private int batchSize;

    @Scheduled(
            fixedDelayString = "${seller.earning-release.fixed-delay-ms:600000}",
            initialDelayString = "${seller.earning-release.initial-delay-ms:60000}"
    )
    public void releaseDueSellerEarnings() {
        log.info("Scheduled seller earning release started: batchSize={}", batchSize);

        try {
            SellerEarningBatchReleaseResult result =
                    sellerEarningBatchReleaseService.releaseDueEarnings(batchSize);

            log.info(
                    "Scheduled seller earning release completed: scanned={}, released={}, skipped={}, failed={}",
                    result.getScannedCount(),
                    result.getReleasedCount(),
                    result.getSkippedCount(),
                    result.getFailedCount()
            );
        } catch (RuntimeException ex) {
            log.error(
                    "Scheduled seller earning release failed: exceptionType={}",
                    ex.getClass().getSimpleName()
            );
        }
    }
}