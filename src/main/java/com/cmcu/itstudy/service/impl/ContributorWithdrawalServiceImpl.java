package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.dto.contributor.ContributorWithdrawalCreateRequestDto;
import com.cmcu.itstudy.dto.contributor.ContributorWithdrawalResponseDto;
import com.cmcu.itstudy.dto.contributor.withdrawal.ContributorWithdrawalDetailResponseDto;
import com.cmcu.itstudy.dto.contributor.withdrawal.ContributorWithdrawalHistoryItemDto;
import com.cmcu.itstudy.dto.contributor.withdrawal.ContributorWithdrawalPageResponseDto;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
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
                    return new IllegalArgumentException(
                            "Payout profile is not configured"
                    );
                });

        // Guard: profile must be complete before a withdrawal can be created.
        // Source of truth — rejects invalid legacy profiles too.
        assertPayoutProfileValid(profile);

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

    /**
     * Guard: payout profile must be complete and well-formed before a
     * withdrawal can be created. Mirrors the validation applied on upsert
     * (see SellerPayoutProfileUpdateRequestDto) so legacy invalid profiles
     * are also rejected. Throws IllegalArgumentException → HTTP 400 via
     * the project's GlobalExceptionHandler.
     */
    private static void assertPayoutProfileValid(SellerPayoutProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException(
                    "Payout profile is not configured"
            );
        }
        if (isBlank(profile.getBankCode())
                || isBlank(profile.getBankName())
                || isBlank(profile.getBankAccountHolderName())) {
            throw new IllegalArgumentException(
                    "Payout profile is incomplete. Please complete your payout profile before creating a withdrawal request."
            );
        }
        String account = profile.getBankAccountNumber();
        if (account == null || !account.trim().matches("^[0-9]{7,19}$")) {
            throw new IllegalArgumentException(
                    "Payout profile bank account number is invalid. Please update your payout profile before creating a withdrawal request."
            );
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
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
                .bankAccountNumber(
                        withdrawalRequest.getBankAccountNumber()
                )
                .bankAccountHolderName(withdrawalRequest.getBankAccountHolderName())
                .sellerNote(withdrawalRequest.getSellerNote())
                .createdAt(withdrawalRequest.getCreatedAt())
                .updatedAt(withdrawalRequest.getUpdatedAt())
                .build();
    }

    private static String resolveRejectionReason(WithdrawalRequest withdrawal) {
        if (withdrawal.getStatus() != WithdrawalStatus.REJECTED) {
            return null;
        }
        String adminNote = withdrawal.getAdminNote();
        if (adminNote == null || adminNote.isBlank()) {
            return null;
        }
        return adminNote.trim();
    }

    /**
     * Expose the processing / transaction note to the contributor only when
     * the withdrawal is PAID. PENDING and REJECTED return null so we never
     * leak moderator scratch notes mid-flow.
     */
    private static String resolveProcessingNote(WithdrawalRequest withdrawal) {
        if (withdrawal.getStatus() != WithdrawalStatus.PAID) {
            return null;
        }
        String adminNote = withdrawal.getAdminNote();
        if (adminNote == null || adminNote.isBlank()) {
            return null;
        }
        return adminNote.trim();
    }

    private static ContributorWithdrawalHistoryItemDto toHistoryItemDto(
            WithdrawalRequest withdrawal
    ) {
        return ContributorWithdrawalHistoryItemDto.builder()
                .id(withdrawal.getId())
                .requestCode(withdrawal.getRequestCode())
                .amount(withdrawal.getAmount())
                .status(withdrawal.getStatus())
                .bankCode(withdrawal.getBankCode())
                .bankName(withdrawal.getBankName())
                .bankAccountNumber(withdrawal.getBankAccountNumber())
                .bankAccountHolderName(withdrawal.getBankAccountHolderName())
                .sellerNote(withdrawal.getSellerNote())
                .rejectionReason(resolveRejectionReason(withdrawal))
                .adminNote(resolveProcessingNote(withdrawal))
                .createdAt(withdrawal.getCreatedAt())
                .updatedAt(withdrawal.getUpdatedAt())
                .approvedAt(withdrawal.getApprovedAt())
                .paidAt(withdrawal.getPaidAt())
                .rejectedAt(withdrawal.getRejectedAt())
                .cancelledAt(withdrawal.getCancelledAt())
                .build();
    }

    private static ContributorWithdrawalDetailResponseDto toDetailDto(
            WithdrawalRequest withdrawal
    ) {
        return ContributorWithdrawalDetailResponseDto.builder()
                .id(withdrawal.getId())
                .requestCode(withdrawal.getRequestCode())
                .amount(withdrawal.getAmount())
                .status(withdrawal.getStatus())
                .bankCode(withdrawal.getBankCode())
                .bankName(withdrawal.getBankName())
                .bankAccountNumber(withdrawal.getBankAccountNumber())
                .bankAccountHolderName(withdrawal.getBankAccountHolderName())
                .sellerNote(withdrawal.getSellerNote())
                .rejectionReason(resolveRejectionReason(withdrawal))
                .adminNote(resolveProcessingNote(withdrawal))
                .createdAt(withdrawal.getCreatedAt())
                .updatedAt(withdrawal.getUpdatedAt())
                .approvedAt(withdrawal.getApprovedAt())
                .paidAt(withdrawal.getPaidAt())
                .rejectedAt(withdrawal.getRejectedAt())
                .cancelledAt(withdrawal.getCancelledAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ContributorWithdrawalPageResponseDto getWithdrawalHistory(
            UUID sellerId,
            int page,
            int size,
            WithdrawalStatus status
    ) {
        if (page < 0) {
            throw new IllegalArgumentException(
                    "Page index must not be negative"
            );
        }
        if (size <= 0) {
            throw new IllegalArgumentException(
                    "Page size must be greater than zero"
            );
        }
        if (size > 100) {
            throw new IllegalArgumentException(
                    "Page size must not exceed 100"
            );
        }

        Sort sort = Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id")
        );
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<WithdrawalRequest> result;
        if (status != null) {
            result = withdrawalRequestRepository.findAllBySellerIdAndStatus(
                    sellerId,
                    status,
                    pageable
            );
        } else {
            result = withdrawalRequestRepository.findAllBySellerId(
                    sellerId,
                    pageable
            );
        }

        List<ContributorWithdrawalHistoryItemDto> content = result.getContent()
                .stream()
                .map(ContributorWithdrawalServiceImpl::toHistoryItemDto)
                .toList();

        return ContributorWithdrawalPageResponseDto.builder()
                .content(content)
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ContributorWithdrawalDetailResponseDto getWithdrawalDetail(
            UUID sellerId,
            UUID withdrawalId
    ) {
        WithdrawalRequest withdrawal = withdrawalRequestRepository
                .findByIdAndSellerId(withdrawalId, sellerId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Withdrawal request not found"
                ));

        return toDetailDto(withdrawal);
    }

}
