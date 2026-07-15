package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.dto.contributor.ContributorWithdrawalCreateRequestDto;
import com.cmcu.itstudy.dto.contributor.ContributorWithdrawalResponseDto;
import com.cmcu.itstudy.entity.SellerPayoutProfile;
import com.cmcu.itstudy.entity.WithdrawalRequest;
import com.cmcu.itstudy.enums.WithdrawalStatus;
import com.cmcu.itstudy.handle.WithdrawalIdempotencyConflictException;
import com.cmcu.itstudy.repository.SellerPayoutProfileRepository;
import com.cmcu.itstudy.repository.UserRepository;
import com.cmcu.itstudy.repository.WithdrawalRequestRepository;
import com.cmcu.itstudy.service.contract.ContributorWithdrawalService;
import com.cmcu.itstudy.service.contract.SellerBalanceService;
import com.cmcu.itstudy.service.dto.ContributorWithdrawalCreateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ContributorWithdrawalServiceImpl implements ContributorWithdrawalService {

    private static final String MASK_ALL = "****";
    private static final String MASKED_PREFIX = "********";

    private static final long MIN_AMOUNT = 5_001L;
    private static final long MAX_AMOUNT = 999_999L;
    private static final int SELLER_NOTE_MAX_LENGTH = 1000;

    private static final String IDEMPOTENCY_CONFLICT_MESSAGE =
            "Client request ID was already used with different withdrawal data";

    private final UserRepository userRepository;
    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final SellerPayoutProfileRepository sellerPayoutProfileRepository;
    private final SellerBalanceService sellerBalanceService;

    @Override
    public ContributorWithdrawalCreateResult createWithdrawal(
            UUID sellerId,
            ContributorWithdrawalCreateRequestDto createRequest
    ) {
        if (sellerId == null) {
            throw new IllegalArgumentException("sellerId is required");
        }
        if (createRequest == null) {
            throw new IllegalArgumentException("request is required");
        }

        Long amount = createRequest.getAmount();
        if (amount == null) {
            throw new IllegalArgumentException("amount is required");
        }
        if (amount < MIN_AMOUNT) {
            throw new IllegalArgumentException("Withdrawal amount must be at least 5001");
        }
        if (amount > MAX_AMOUNT) {
            throw new IllegalArgumentException("Withdrawal amount must not exceed 999999");
        }

        UUID clientRequestId = createRequest.getClientRequestId();
        if (clientRequestId == null) {
            throw new IllegalArgumentException("Client request ID is required");
        }

        String normalizedSellerNote = normalizeSellerNote(createRequest.getSellerNote());
        if (normalizedSellerNote != null
                && normalizedSellerNote.length() > SELLER_NOTE_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Seller note must not exceed 1000 characters"
            );
        }

        userRepository.findByIdForUpdate(sellerId)
                .orElseThrow(() -> {
                    log.error(
                            "CRITICAL: Seller User not found while creating withdrawal. sellerId={}",
                            sellerId
                    );
                    return new IllegalStateException("Seller User not found");
                });

        Optional<WithdrawalRequest> existingOpt =
                withdrawalRequestRepository.findBySellerIdAndClientRequestId(
                        sellerId,
                        clientRequestId
                );

        if (existingOpt.isPresent()) {
            WithdrawalRequest existing = existingOpt.get();

            String existingNormalizedNote = normalizeSellerNote(existing.getSellerNote());

            boolean samePayload = Objects.equals(existing.getAmount(), amount)
                    && Objects.equals(existingNormalizedNote, normalizedSellerNote);

            if (samePayload) {
                log.info(
                        "Withdrawal replay: sellerId={}, withdrawalRequestId={}, requestCode={}",
                        sellerId,
                        existing.getId(),
                        existing.getRequestCode()
                );

                ContributorWithdrawalResponseDto replayResponse = toResponse(existing);

                return ContributorWithdrawalCreateResult.builder()
                        .data(replayResponse)
                        .created(false)
                        .build();
            }

            log.error(
                    "CRITICAL: WithdrawalIdempotencyConflict. sellerId={}",
                    sellerId
            );
            throw new WithdrawalIdempotencyConflictException(IDEMPOTENCY_CONFLICT_MESSAGE);
        }

        SellerPayoutProfile profile = sellerPayoutProfileRepository
                .findBySellerIdForUpdate(sellerId)
                .orElseThrow(() -> {
                    log.error(
                            "CRITICAL: Payout profile not configured while creating withdrawal. sellerId={}",
                            sellerId
                    );
                    return new IllegalStateException("Payout profile is not configured");
                });

        sellerBalanceService.reserveAvailableToLocked(sellerId, amount);

        WithdrawalRequest withdrawalRequest = WithdrawalRequest.builder()
                .requestCode(generateRequestCode())
                .clientRequestId(clientRequestId)
                .sellerId(sellerId)
                .amount(amount)
                .status(WithdrawalStatus.PENDING)
                .bankCode(profile.getBankCode())
                .bankName(profile.getBankName())
                .bankAccountNumber(profile.getBankAccountNumber())
                .bankAccountHolderName(profile.getBankAccountHolderName())
                .sellerNote(normalizedSellerNote)
                .build();

        WithdrawalRequest savedWithdrawal =
                withdrawalRequestRepository.saveAndFlush(withdrawalRequest);

        log.info(
                "Contributor withdrawal created: sellerId={}, withdrawalRequestId={}, requestCode={}, amount={}",
                sellerId,
                savedWithdrawal.getId(),
                savedWithdrawal.getRequestCode(),
                savedWithdrawal.getAmount()
        );

        ContributorWithdrawalResponseDto response = toResponse(savedWithdrawal);

        return ContributorWithdrawalCreateResult.builder()
                .data(response)
                .created(true)
                .build();
    }

    private static String generateRequestCode() {
        String hex = UUID.randomUUID()
                .toString()
                .replace("-", "");
        return "WD" + hex.substring(0, 30);
    }

    private static String normalizeSellerNote(String sellerNote) {
        if (sellerNote == null) {
            return null;
        }
        String trimmed = sellerNote.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
    }

    private static String maskBankAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            return MASK_ALL;
        }
        String normalized = accountNumber.trim();
        if (normalized.length() <= 4) {
            return MASK_ALL;
        }
        String last4 = normalized.substring(normalized.length() - 4);
        return MASKED_PREFIX + last4;
    }

    private ContributorWithdrawalResponseDto toResponse(WithdrawalRequest withdrawalRequest) {
        return ContributorWithdrawalResponseDto.builder()
                .id(withdrawalRequest.getId())
                .requestCode(withdrawalRequest.getRequestCode())
                .clientRequestId(withdrawalRequest.getClientRequestId())
                .amount(withdrawalRequest.getAmount())
                .status(withdrawalRequest.getStatus())
                .bankCode(withdrawalRequest.getBankCode())
                .bankName(withdrawalRequest.getBankName())
                .maskedBankAccountNumber(
                        maskBankAccountNumber(
                                withdrawalRequest.getBankAccountNumber()
                        )
                )
                .bankAccountHolderName(withdrawalRequest.getBankAccountHolderName())
                .sellerNote(withdrawalRequest.getSellerNote())
                .createdAt(withdrawalRequest.getCreatedAt())
                .updatedAt(withdrawalRequest.getUpdatedAt())
                .build();
    }

}
