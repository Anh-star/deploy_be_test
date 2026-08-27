package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.dto.paymentmoderator.withdrawal.PaymentModeratorWithdrawalDetailResponseDto;
import com.cmcu.itstudy.dto.paymentmoderator.withdrawal.PaymentModeratorWithdrawalListItemDto;
import com.cmcu.itstudy.dto.paymentmoderator.withdrawal.PaymentModeratorWithdrawalPageResponseDto;
import com.cmcu.itstudy.entity.User;
import com.cmcu.itstudy.entity.WithdrawalRequest;
import com.cmcu.itstudy.enums.WithdrawalStatus;
import com.cmcu.itstudy.repository.UserRepository;
import com.cmcu.itstudy.repository.WithdrawalRequestRepository;
import com.cmcu.itstudy.service.contract.PaymentModeratorWithdrawalQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentModeratorWithdrawalQueryServiceImpl
        implements PaymentModeratorWithdrawalQueryService {

    private static final String MASK_ALL = "****";
    private static final String MASKED_PREFIX = "********";
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final UserRepository userRepository;

    @Override
    public PaymentModeratorWithdrawalPageResponseDto listWithdrawals(
            int page,
            int size,
            WithdrawalStatus status,
            String search,
            java.time.LocalDateTime startDate,
            java.time.LocalDateTime endDate
    ) {
        int normalizedPage = Math.max(0, page);
        int normalizedSize = size > 0
                ? Math.min(size, MAX_PAGE_SIZE)
                : DEFAULT_PAGE_SIZE;

        String normalizedSearch =
                search != null && !search.isBlank()
                        ? search.trim()
                        : null;

        Sort sort = Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id")
        );

        Pageable pageable = PageRequest.of(
                normalizedPage,
                normalizedSize,
                sort
        );

        Page<WithdrawalRequest> result =
                withdrawalRequestRepository.searchForPaymentModerator(
                        status,
                        normalizedSearch,
                        startDate,
                        endDate,
                        pageable
                );

        Set<UUID> sellerIds = result.getContent()
                .stream()
                .map(WithdrawalRequest::getSellerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, User> sellerMap =
                userRepository.findAllById(sellerIds)
                        .stream()
                        .collect(Collectors.toMap(
                                User::getId,
                                Function.identity()
                        ));

        var content = result.getContent().stream()
                .map(wr -> {
                    User seller = sellerMap.get(wr.getSellerId());
                    return toListItemDto(wr, seller);
                })
                .collect(Collectors.toList());

        return PaymentModeratorWithdrawalPageResponseDto.builder()
                .content(content)
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @Override
    public PaymentModeratorWithdrawalDetailResponseDto getWithdrawal(UUID withdrawalId) {
        WithdrawalRequest wr = withdrawalRequestRepository.findById(withdrawalId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Withdrawal request not found"
                ));

        User seller = userRepository.findById(wr.getSellerId()).orElse(null);

        return toDetailDto(wr, seller);
    }

    private static PaymentModeratorWithdrawalListItemDto toListItemDto(
            WithdrawalRequest wr,
            User seller
    ) {
        return PaymentModeratorWithdrawalListItemDto.builder()
                .id(wr.getId())
                .requestCode(wr.getRequestCode())
                .sellerId(wr.getSellerId())
                .sellerEmail(seller != null ? seller.getEmail() : null)
                .sellerFullName(seller != null ? seller.getFullName() : null)
                .amount(wr.getAmount())
                .status(wr.getStatus())
                .bankCode(wr.getBankCode())
                .bankName(wr.getBankName())
                .maskedBankAccountNumber(
                        maskBankAccountNumber(wr.getBankAccountNumber())
                )
                .bankAccountHolderName(wr.getBankAccountHolderName())
                .createdAt(wr.getCreatedAt())
                .updatedAt(wr.getUpdatedAt())
                .build();
    }

    private static PaymentModeratorWithdrawalDetailResponseDto toDetailDto(
            WithdrawalRequest wr,
            User seller
    ) {
        return PaymentModeratorWithdrawalDetailResponseDto.builder()
                .id(wr.getId())
                .requestCode(wr.getRequestCode())
                .clientRequestId(wr.getClientRequestId())
                .sellerId(wr.getSellerId())
                .sellerEmail(seller != null ? seller.getEmail() : null)
                .sellerFullName(seller != null ? seller.getFullName() : null)
                .amount(wr.getAmount())
                .status(wr.getStatus())
                .bankCode(wr.getBankCode())
                .bankName(wr.getBankName())
                .bankAccountNumber(wr.getBankAccountNumber())
                .bankAccountHolderName(wr.getBankAccountHolderName())
                .sellerNote(wr.getSellerNote())
                .adminNote(wr.getAdminNote())
                .approvedByAdminId(wr.getApprovedByAdminId())
                .paidByAdminId(wr.getPaidByAdminId())
                .rejectedByAdminId(wr.getRejectedByAdminId())
                .approvedAt(wr.getApprovedAt())
                .paidAt(wr.getPaidAt())
                .rejectedAt(wr.getRejectedAt())
                .cancelledAt(wr.getCancelledAt())
                .createdAt(wr.getCreatedAt())
                .updatedAt(wr.getUpdatedAt())
                .build();
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
}
