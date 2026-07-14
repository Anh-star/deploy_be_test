package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.enums.SellerEarningStatus;
import com.cmcu.itstudy.repository.SellerEarningRepository;
import com.cmcu.itstudy.service.contract.SellerEarningBatchReleaseService;
import com.cmcu.itstudy.service.contract.SellerEarningReleaseService;
import com.cmcu.itstudy.service.dto.SellerEarningBatchReleaseResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SellerEarningBatchReleaseServiceImpl implements SellerEarningBatchReleaseService {

    private static final int MIN_BATCH_SIZE = 1;
    private static final int MAX_BATCH_SIZE = 500;

    private final SellerEarningRepository sellerEarningRepository;
    private final SellerEarningReleaseService sellerEarningReleaseService;

    @Override
    public SellerEarningBatchReleaseResult releaseDueEarnings(int batchSize) {
        if (batchSize < MIN_BATCH_SIZE || batchSize > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "batchSize must be between " + MIN_BATCH_SIZE + " and " + MAX_BATCH_SIZE);
        }

        LocalDateTime now = LocalDateTime.now();
        List<UUID> earningIds = sellerEarningRepository.findDueEarningIds(
                SellerEarningStatus.PENDING,
                now,
                PageRequest.of(0, batchSize)
        );

        int releasedCount = 0;
        int skippedCount = 0;
        int failedCount = 0;

        for (UUID earningId : earningIds) {
            try {
                boolean released = sellerEarningReleaseService.releaseIfDue(earningId);
                if (released) {
                    releasedCount++;
                } else {
                    skippedCount++;
                }
            } catch (RuntimeException ex) {
                failedCount++;
                log.error(
                        "Batch seller earning release failed: earningId={}, exceptionType={}",
                        earningId,
                        ex.getClass().getSimpleName()
                );
            }
        }

        log.info(
                "Batch seller earning release completed: scanned={}, released={}, skipped={}, failed={}",
                earningIds.size(), releasedCount, skippedCount, failedCount
        );

        return SellerEarningBatchReleaseResult.builder()
                .scannedCount(earningIds.size())
                .releasedCount(releasedCount)
                .skippedCount(skippedCount)
                .failedCount(failedCount)
                .build();
    }
}