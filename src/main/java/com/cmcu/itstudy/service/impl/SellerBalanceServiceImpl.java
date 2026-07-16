package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.entity.SellerBalance;
import com.cmcu.itstudy.repository.SellerBalanceRepository;
import com.cmcu.itstudy.repository.UserRepository;
import com.cmcu.itstudy.service.contract.SellerBalanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Override
    public SellerBalance movePendingToAvailable(UUID sellerId, Long amount, UUID earningId) {
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
                            "CRITICAL: Seller User not found while moving pending to available. sellerId={}, earningId={}",
                            sellerId,
                            earningId);
                    return new IllegalStateException("Seller User not found: " + sellerId);
                });

        SellerBalance balance = sellerBalanceRepository.findBySellerIdForUpdate(sellerId)
                .orElseThrow(() -> {
                    log.error(
                            "CRITICAL: SellerBalance not found while releasing earning. sellerId={}, earningId={}",
                            sellerId,
                            earningId);
                    return new IllegalStateException("SellerBalance not found for seller: " + sellerId);
                });

        validateNonNegative(balance, sellerId, earningId);

        if (balance.getPendingBalance() < amount) {
            log.error(
                    "CRITICAL: Insufficient pendingBalance while releasing earning. sellerId={}, earningId={}, pendingBalance={}, amount={}",
                    sellerId,
                    earningId,
                    balance.getPendingBalance(),
                    amount);
            throw new IllegalStateException(
                    "Insufficient pendingBalance for seller " + sellerId
                            + ": pending=" + balance.getPendingBalance()
                            + ", amount=" + amount);
        }

        long newPending = Math.subtractExact(balance.getPendingBalance(), amount);
        long newAvailable = Math.addExact(balance.getAvailableBalance(), amount);

        balance.setPendingBalance(newPending);
        balance.setAvailableBalance(newAvailable);

        SellerBalance saved = sellerBalanceRepository.save(balance);

        log.info(
                "SellerBalance movePendingToAvailable: sellerId={}, earningId={}, amount={}, newPending={}, newAvailable={}",
                sellerId,
                earningId,
                amount,
                newPending,
                newAvailable);

        return saved;
    }

    @Override
    public SellerBalance reserveAvailableToLocked(UUID sellerId, Long amount) {
        if (sellerId == null) {
            throw new IllegalArgumentException("sellerId must not be null");
        }
        if (amount == null || amount <= 0L) {
            throw new IllegalArgumentException("amount must be positive");
        }

        userRepository.findByIdForUpdate(sellerId)
                .orElseThrow(() -> {
                    log.error(
                            "CRITICAL: Seller User not found while reserving available to locked. sellerId={}",
                            sellerId);
                    return new IllegalStateException("Seller User not found: " + sellerId);
                });

        SellerBalance balance = sellerBalanceRepository.findBySellerIdForUpdate(sellerId)
                .orElseThrow(() -> {
                    log.error(
                            "CRITICAL: SellerBalance not found while reserving available to locked. sellerId={}",
                            sellerId);
                    return new IllegalStateException("Seller balance is not available");
                });

        validateNonNegativeForWithdrawal(balance, sellerId);

        if (balance.getAvailableBalance() < amount) {
            log.error(
                    "CRITICAL: Insufficient availableBalance while reserving for withdrawal. sellerId={}, amount={}",
                    sellerId,
                    amount);
            throw new IllegalStateException("Insufficient available balance");
        }

        long newAvailable = Math.subtractExact(balance.getAvailableBalance(), amount);
        long newLocked = Math.addExact(balance.getLockedBalance(), amount);

        balance.setAvailableBalance(newAvailable);
        balance.setLockedBalance(newLocked);

        SellerBalance saved = sellerBalanceRepository.save(balance);

        log.info(
                "SellerBalance reserveAvailableToLocked: sellerId={}, amount={}, newAvailable={}, newLocked={}",
                sellerId,
                amount,
                newAvailable,
                newLocked);

        return saved;
    }

    @Override
    public SellerBalance releaseLockedToAvailable(UUID sellerId, Long amount) {
        if (sellerId == null) {
            throw new IllegalArgumentException("Seller ID is required");
        }
        if (amount == null || amount <= 0L) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        userRepository.findByIdForUpdate(sellerId)
                .orElseThrow(() -> {
                    log.error(
                            "CRITICAL: Seller User not found while releasing locked to available. sellerId={}",
                            sellerId);
                    return new IllegalStateException("Seller user not found");
                });

        SellerBalance balance = sellerBalanceRepository.findBySellerIdForUpdate(sellerId)
                .orElseThrow(() -> {
                    log.error(
                            "CRITICAL: SellerBalance not found while releasing locked to available. sellerId={}",
                            sellerId);
                    return new IllegalStateException("Seller balance not found");
                });

        validateNonNegativeForWithdrawal(balance, sellerId);

        if (balance.getLockedBalance() == null
                || balance.getAvailableBalance() == null
                || balance.getLockedBalance() < amount) {
            log.error(
                    "CRITICAL: Insufficient lockedBalance while releasing for withdrawal. sellerId={}, amount={}",
                    sellerId,
                    amount);
            throw new IllegalStateException("Insufficient locked balance");
        }

        long newLockedBalance = Math.subtractExact(
                balance.getLockedBalance(),
                amount
        );
        long newAvailableBalance = Math.addExact(
                balance.getAvailableBalance(),
                amount
        );

        balance.setLockedBalance(newLockedBalance);
        balance.setAvailableBalance(newAvailableBalance);

        SellerBalance saved = sellerBalanceRepository.save(balance);

        log.info(
                "SellerBalance releaseLockedToAvailable: sellerId={}, amount={}, newLockedBalance={}, newAvailableBalance={}",
                sellerId,
                amount,
                newLockedBalance,
                newAvailableBalance);

        return saved;
    }

    @Override
    public SellerBalance moveLockedToWithdrawn(UUID sellerId, Long amount) {
        if (sellerId == null) {
            throw new IllegalArgumentException("Seller ID is required");
        }
        if (amount == null || amount <= 0L) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        userRepository.findByIdForUpdate(sellerId)
                .orElseThrow(() -> {
                    log.error(
                            "CRITICAL: Seller User not found while moving locked to withdrawn. sellerId={}",
                            sellerId);
                    return new IllegalStateException("Seller user not found");
                });

        SellerBalance balance = sellerBalanceRepository.findBySellerIdForUpdate(sellerId)
                .orElseThrow(() -> {
                    log.error(
                            "CRITICAL: SellerBalance not found while moving locked to withdrawn. sellerId={}",
                            sellerId);
                    return new IllegalStateException("Seller balance not found");
                });

        validateNonNegativeForWithdrawal(balance, sellerId);

        if (balance.getLockedBalance() == null
                || balance.getTotalEarned() == null
                || balance.getTotalWithdrawn() == null
                || balance.getLockedBalance() < amount) {
            log.error(
                    "CRITICAL: Insufficient lockedBalance while moving locked to withdrawn. sellerId={}, amount={}, lockedBalance={}",
                    sellerId,
                    amount,
                    balance.getLockedBalance());
            throw new IllegalStateException("Insufficient locked balance");
        }

        long newLockedBalance = Math.subtractExact(
                balance.getLockedBalance(),
                amount
        );
        long newTotalWithdrawn = Math.addExact(
                balance.getTotalWithdrawn(),
                amount
        );

        if (newTotalWithdrawn > balance.getTotalEarned()) {
            log.error(
                    "CRITICAL: totalWithdrawn would exceed totalEarned. sellerId={}, amount={}, newTotalWithdrawn={}, totalEarned={}",
                    sellerId,
                    amount,
                    newTotalWithdrawn,
                    balance.getTotalEarned());
            throw new IllegalStateException(
                    "Total withdrawn exceeds total earned"
            );
        }

        balance.setLockedBalance(newLockedBalance);
        balance.setTotalWithdrawn(newTotalWithdrawn);

        SellerBalance saved = sellerBalanceRepository.save(balance);

        log.info(
                "SellerBalance moveLockedToWithdrawn: sellerId={}, amount={}, newLockedBalance={}, newTotalWithdrawn={}",
                sellerId,
                amount,
                newLockedBalance,
                newTotalWithdrawn);

        return saved;
    }

    private void validateNonNegativeForWithdrawal(SellerBalance balance, UUID sellerId) {
        if (balance.getPendingBalance() == null || balance.getPendingBalance() < 0L) {
            log.error(
                    "CRITICAL: Invalid SellerBalance.pendingBalance for sellerId={}, field=pendingBalance, value={}",
                    sellerId,
                    balance.getPendingBalance());
            throw new IllegalStateException(
                    "Invalid pendingBalance for seller " + sellerId + ": " + balance.getPendingBalance());
        }
        if (balance.getAvailableBalance() == null || balance.getAvailableBalance() < 0L) {
            log.error(
                    "CRITICAL: Invalid SellerBalance.availableBalance for sellerId={}, field=availableBalance, value={}",
                    sellerId,
                    balance.getAvailableBalance());
            throw new IllegalStateException(
                    "Invalid availableBalance for seller " + sellerId + ": " + balance.getAvailableBalance());
        }
        if (balance.getLockedBalance() == null || balance.getLockedBalance() < 0L) {
            log.error(
                    "CRITICAL: Invalid SellerBalance.lockedBalance for sellerId={}, field=lockedBalance, value={}",
                    sellerId,
                    balance.getLockedBalance());
            throw new IllegalStateException(
                    "Invalid lockedBalance for seller " + sellerId + ": " + balance.getLockedBalance());
        }
        if (balance.getTotalEarned() == null || balance.getTotalEarned() < 0L) {
            log.error(
                    "CRITICAL: Invalid SellerBalance.totalEarned for sellerId={}, field=totalEarned, value={}",
                    sellerId,
                    balance.getTotalEarned());
            throw new IllegalStateException(
                    "Invalid totalEarned for seller " + sellerId + ": " + balance.getTotalEarned());
        }
        if (balance.getTotalWithdrawn() == null || balance.getTotalWithdrawn() < 0L) {
            log.error(
                    "CRITICAL: Invalid SellerBalance.totalWithdrawn for sellerId={}, field=totalWithdrawn, value={}",
                    sellerId,
                    balance.getTotalWithdrawn());
            throw new IllegalStateException(
                    "Invalid totalWithdrawn for seller " + sellerId + ": " + balance.getTotalWithdrawn());
        }
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