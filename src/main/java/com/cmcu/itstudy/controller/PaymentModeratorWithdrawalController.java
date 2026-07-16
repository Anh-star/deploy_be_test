package com.cmcu.itstudy.controller;

import com.cmcu.itstudy.dto.common.ApiResponse;
import com.cmcu.itstudy.dto.paymentmoderator.withdrawal.PaymentModeratorWithdrawalDetailResponseDto;
import com.cmcu.itstudy.dto.paymentmoderator.withdrawal.PaymentModeratorWithdrawalPageResponseDto;
import com.cmcu.itstudy.enums.WithdrawalStatus;
import com.cmcu.itstudy.service.contract.PaymentModeratorWithdrawalQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/payment-moderator/withdrawals")
@RequiredArgsConstructor
public class PaymentModeratorWithdrawalController {

    private final PaymentModeratorWithdrawalQueryService
            paymentModeratorWithdrawalQueryService;

    @GetMapping
    @PreAuthorize("hasRole('PAYMENT_MODERATOR')")
    public ResponseEntity<ApiResponse<PaymentModeratorWithdrawalPageResponseDto>> listWithdrawals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) WithdrawalStatus status,
            @RequestParam(required = false) String search
    ) {
        PaymentModeratorWithdrawalPageResponseDto data =
                paymentModeratorWithdrawalQueryService.listWithdrawals(
                        page,
                        size,
                        status,
                        search
                );

        return ResponseEntity.ok(
                ApiResponse.success(data, "Withdrawal request list")
        );
    }

    @GetMapping("/{withdrawalId}")
    @PreAuthorize("hasRole('PAYMENT_MODERATOR')")
    public ResponseEntity<ApiResponse<PaymentModeratorWithdrawalDetailResponseDto>> getWithdrawal(
            @PathVariable UUID withdrawalId
    ) {
        PaymentModeratorWithdrawalDetailResponseDto data =
                paymentModeratorWithdrawalQueryService.getWithdrawal(withdrawalId);

        return ResponseEntity.ok(
                ApiResponse.success(data, "Withdrawal request detail")
        );
    }
}
