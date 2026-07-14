package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.entity.SellerBalance;
import com.cmcu.itstudy.repository.SellerBalanceRepository;
import com.cmcu.itstudy.repository.UserRepository;
import com.cmcu.itstudy.service.contract.SellerBalanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SellerBalanceServiceImpl implements SellerBalanceService {

    private final SellerBalanceRepository sellerBalanceRepository;
    private final UserRepository userRepository;

    @Override
    public SellerBalance creditPending(UUID sellerId, Long amount, UUID earningId) {
        if (sellerId == null) {
            throw new IllegalArgumentException("sellerId must not be null");
        }
        if (earningId == null) {
            throw new IllegalArgumentException("earningId must not be null");
        }
        if (amount == null || amount <= 0L) {
            throw new IllegalArgumentException("amount must be positive, got: " + amount);
        }

        userRepository.findByIdForUpdate(sellerId)
                .orElseThrow(() -> {
                    log.error(
                            "CRITICAL: Seller User not found while crediting balance. sellerId={}, earningId={}",
                            sellerId,
                            earningId);
                    return new IllegalStateException("Seller User not found: " + sellerId);
                });

        SellerBalance balance = sellerBalanceRepository.findBySellerIdForUpdate(sellerId)
                .orElseGet(() -> SellerBalance.builder()
                        .sellerId(sellerId)
                        .pendingBalance(0L)
                        .availableBalance(0L)
                        .lockedBalance(0L)
                        .totalEarned(0L)
                        .totalWithdrawn(0L)
                        .build());

        validateNonNegative(balance, sellerId, earningId);

        long newPending = Math.addExact(balance.getPendingBalance(), amount);
        long newTotalEarned = Math.addExact(balance.getTotalEarned(), amount);

        balance.setPendingBalance(newPending);
        balance.setTotalEarned(newTotalEarned);

        SellerBalance saved = sellerBalanceRepository.save(balance);

        log.info(
                "SellerBalance creditPending: sellerId={}, earningId={}, amount={}, newPending={}, newTotalEarned={}",
                sellerId,
                earningId,
                amount,
                newPending,
                newTotalEarned);

        return saved;
    }

    private void validateNonNegative(SellerBalance balance, UUID sellerId, UUID earningId) {
        if (balance.getPendingBalance() == null || balance.getPendingBalance() < 0L) {
            log.error(
                    "CRITICAL: Invalid SellerBalance.pendingBalance for sellerId={}, earningId={}, value={}",
                    sellerId,
                    earningId,
                    balance.getPendingBalance());
            throw new IllegalStateException(
                    "Invalid pendingBalance for seller " + sellerId + ": " + balance.getPendingBalance());
        }
        if (balance.getAvailableBalance() == null || balance.getAvailableBalance() < 0L) {
            log.error(
                    "CRITICAL: Invalid SellerBalance.availableBalance for sellerId={}, earningId={}, value={}",
                    sellerId,
                    earningId,
                    balance.getAvailableBalance());
            throw new IllegalStateException(
                    "Invalid availableBalance for seller " + sellerId + ": " + balance.getAvailableBalance());
        }
        if (balance.getLockedBalance() == null || balance.getLockedBalance() < 0L) {
            log.error(
                    "CRITICAL: Invalid SellerBalance.lockedBalance for sellerId={}, earningId={}, value={}",
                    sellerId,
                    earningId,
                    balance.getLockedBalance());
            throw new IllegalStateException(
                    "Invalid lockedBalance for seller " + sellerId + ": " + balance.getLockedBalance());
        }
        if (balance.getTotalEarned() == null || balance.getTotalEarned() < 0L) {
            log.error(
                    "CRITICAL: Invalid SellerBalance.totalEarned for sellerId={}, earningId={}, value={}",
                    sellerId,
                    earningId,
                    balance.getTotalEarned());
            throw new IllegalStateException(
                    "Invalid totalEarned for seller " + sellerId + ": " + balance.getTotalEarned());
        }
        if (balance.getTotalWithdrawn() == null || balance.getTotalWithdrawn() < 0L) {
            log.error(
                    "CRITICAL: Invalid SellerBalance.totalWithdrawn for sellerId={}, earningId={}, value={}",
                    sellerId,
                    earningId,
                    balance.getTotalWithdrawn());
            throw new IllegalStateException(
                    "Invalid totalWithdrawn for seller " + sellerId + ": " + balance.getTotalWithdrawn());
        }
    }
}