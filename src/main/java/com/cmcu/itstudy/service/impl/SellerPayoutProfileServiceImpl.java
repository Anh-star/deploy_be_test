package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.dto.contributor.SellerPayoutProfileResponseDto;
import com.cmcu.itstudy.dto.contributor.SellerPayoutProfileUpdateRequestDto;
import com.cmcu.itstudy.entity.SellerPayoutProfile;
import com.cmcu.itstudy.repository.SellerPayoutProfileRepository;
import com.cmcu.itstudy.repository.UserRepository;
import com.cmcu.itstudy.service.contract.SellerPayoutProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SellerPayoutProfileServiceImpl implements SellerPayoutProfileService {

    private static final String MASK_ALL = "****";
    private static final String MASKED_PREFIX = "********";

    private final SellerPayoutProfileRepository sellerPayoutProfileRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public SellerPayoutProfileResponseDto getCurrentProfile(UUID sellerId) {
        if (sellerId == null) {
            throw new IllegalArgumentException("sellerId is required");
        }

        Optional<SellerPayoutProfile> profileOpt =
                sellerPayoutProfileRepository.findById(sellerId);

        if (profileOpt.isEmpty()) {
            return SellerPayoutProfileResponseDto.builder()
                    .configured(false)
                    .build();
        }

        return toResponse(profileOpt.get());
    }

    @Override
    public SellerPayoutProfileResponseDto upsertCurrentProfile(
            UUID sellerId,
            SellerPayoutProfileUpdateRequestDto request
    ) {
        if (sellerId == null) {
            throw new IllegalArgumentException("sellerId is required");
        }
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }

        String bankCode = normalizeRequired(request.getBankCode(), "bankCode");
        String bankName = normalizeRequired(request.getBankName(), "bankName");
        String bankAccountNumber = normalizeRequired(
                request.getBankAccountNumber(),
                "bankAccountNumber"
        );
        String bankAccountHolderName = normalizeRequired(
                request.getBankAccountHolderName(),
                "bankAccountHolderName"
        );

        userRepository.findByIdForUpdate(sellerId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        Optional<SellerPayoutProfile> existingOpt =
                sellerPayoutProfileRepository.findBySellerIdForUpdate(sellerId);

        boolean created;
        SellerPayoutProfile profile;
        if (existingOpt.isEmpty()) {
            profile = SellerPayoutProfile.builder()
                    .sellerId(sellerId)
                    .bankCode(bankCode)
                    .bankName(bankName)
                    .bankAccountNumber(bankAccountNumber)
                    .bankAccountHolderName(bankAccountHolderName)
                    .build();
            created = true;
        } else {
            profile = existingOpt.get();
            profile.setBankCode(bankCode);
            profile.setBankName(bankName);
            profile.setBankAccountNumber(bankAccountNumber);
            profile.setBankAccountHolderName(bankAccountHolderName);
            created = false;
        }

        SellerPayoutProfile saved = sellerPayoutProfileRepository.saveAndFlush(profile);

        log.info(
                "Seller payout profile upserted: sellerId={}, created={}",
                sellerId,
                created
        );

        return toResponse(saved);
    }

    private String normalizeRequired(String value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return trimmed;
    }

    private String maskBankAccountNumber(String accountNumber) {
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

    private SellerPayoutProfileResponseDto toResponse(SellerPayoutProfile profile) {
        return SellerPayoutProfileResponseDto.builder()
                .configured(true)
                .bankCode(profile.getBankCode())
                .bankName(profile.getBankName())
                .maskedBankAccountNumber(maskBankAccountNumber(profile.getBankAccountNumber()))
                .bankAccountHolderName(profile.getBankAccountHolderName())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }
}