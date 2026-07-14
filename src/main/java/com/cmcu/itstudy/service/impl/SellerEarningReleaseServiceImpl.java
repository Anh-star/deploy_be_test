package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.entity.SellerEarning;
import com.cmcu.itstudy.enums.SellerEarningStatus;
import com.cmcu.itstudy.repository.SellerEarningRepository;
import com.cmcu.itstudy.service.contract.SellerBalanceService;
import com.cmcu.itstudy.service.contract.SellerEarningReleaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SellerEarningReleaseServiceImpl implements SellerEarningReleaseService {

    private final SellerEarningRepository sellerEarningRepository;
    private final SellerBalanceService sellerBalanceService;

    @Override
    @Transactional
    public boolean releaseIfDue(UUID earningId) {
        if (earningId == null) {
            throw new IllegalArgumentException("earningId must not be null");
        }

        SellerEarning earning = sellerEarningRepository.findByIdForUpdate(earningId)
                .orElseThrow(() -> {
                    log.error("CRITICAL: SellerEarning not found while releasing. earningId={}", earningId);
                    return new NoSuchElementException("SellerEarning not found: " + earningId);
                });

        if (earning.getStatus() != SellerEarningStatus.PENDING) {
            log.info(
                    "releaseIfDue skipped: earning is not PENDING. earningId={}, status={}",
                    earningId,
                    earning.getStatus());
            return false;
        }

        if (earning.getSellerId() == null) {
            log.error("CRITICAL: SellerEarning.sellerId is null. earningId={}", earningId);
            throw new IllegalStateException("SellerEarning.sellerId is null: " + earningId);
        }
        if (earning.getSellerNetAmount() == null || earning.getSellerNetAmount() <= 0L) {
            log.error(
                    "CRITICAL: Invalid SellerEarning.sellerNetAmount. earningId={}, value={}",
                    earningId,
                    earning.getSellerNetAmount());
            throw new IllegalStateException(
                    "Invalid sellerNetAmount for earning " + earningId + ": " + earning.getSellerNetAmount());
        }
        if (earning.getAvailableAt() == null) {
            log.error("CRITICAL: SellerEarning.availableAt is null. earningId={}", earningId);
            throw new IllegalStateException("SellerEarning.availableAt is null: " + earningId);
        }

        LocalDateTime now = LocalDateTime.now();
        if (earning.getAvailableAt().isAfter(now)) {
            log.info(
                    "releaseIfDue skipped: earning not due yet. earningId={}, availableAt={}, now={}",
                    earningId,
                    earning.getAvailableAt(),
                    now);
            return false;
        }

        sellerBalanceService.movePendingToAvailable(
                earning.getSellerId(),
                earning.getSellerNetAmount(),
                earning.getId()
        );

        earning.setStatus(SellerEarningStatus.AVAILABLE);
        sellerEarningRepository.save(earning);

        log.info(
                "SellerEarning released PENDING -> AVAILABLE. earningId={}, sellerId={}, amount={}",
                earningId,
                earning.getSellerId(),
                earning.getSellerNetAmount());

        return true;
    }
}