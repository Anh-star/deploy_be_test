package com.cmcu.itstudy.controller;

import com.cmcu.itstudy.dto.common.ApiResponse;
import com.cmcu.itstudy.dto.paymentmoderator.withdrawal.PaymentModeratorWithdrawalActionResponseDto;
import com.cmcu.itstudy.dto.paymentmoderator.withdrawal.PaymentModeratorWithdrawalApproveRequestDto;
import com.cmcu.itstudy.dto.paymentmoderator.withdrawal.PaymentModeratorWithdrawalDetailResponseDto;
import com.cmcu.itstudy.dto.paymentmoderator.withdrawal.PaymentModeratorWithdrawalPageResponseDto;
import com.cmcu.itstudy.dto.paymentmoderator.withdrawal.PaymentModeratorWithdrawalRejectRequestDto;
import com.cmcu.itstudy.enums.WithdrawalStatus;
import com.cmcu.itstudy.security.UserDetailsImpl;
import com.cmcu.itstudy.service.contract.PaymentModeratorWithdrawalCommandService;
import com.cmcu.itstudy.service.contract.PaymentModeratorWithdrawalQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    private final PaymentModeratorWithdrawalCommandService
            paymentModeratorWithdrawalCommandService;

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

    @PostMapping("/{withdrawalId}/approve")
    @PreAuthorize("hasRole('PAYMENT_MODERATOR')")
    public ResponseEntity<ApiResponse<PaymentModeratorWithdrawalActionResponseDto>> approveWithdrawal(
            @PathVariable UUID withdrawalId,
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @Valid @RequestBody PaymentModeratorWithdrawalApproveRequestDto request
    ) {
        UUID moderatorId = requireAuthenticatedUserId(currentUser);

        PaymentModeratorWithdrawalActionResponseDto data =
                paymentModeratorWithdrawalCommandService.approveWithdrawal(
                        withdrawalId,
                        moderatorId,
                        request.getAdminNote()
                );

        return ResponseEntity.ok(
                ApiResponse.success(data, "Withdrawal request approved")
        );
    }

    @PostMapping("/{withdrawalId}/reject")
    @PreAuthorize("hasRole('PAYMENT_MODERATOR')")
    public ResponseEntity<ApiResponse<PaymentModeratorWithdrawalActionResponseDto>> rejectWithdrawal(
            @PathVariable UUID withdrawalId,
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @Valid @RequestBody PaymentModeratorWithdrawalRejectRequestDto request
    ) {
        UUID moderatorId = requireAuthenticatedUserId(currentUser);

        PaymentModeratorWithdrawalActionResponseDto data =
                paymentModeratorWithdrawalCommandService.rejectWithdrawal(
                        withdrawalId,
                        moderatorId,
                        request.getAdminNote()
                );

        return ResponseEntity.ok(
                ApiResponse.success(data, "Withdrawal request rejected")
        );
    }

    private static UUID requireAuthenticatedUserId(UserDetailsImpl currentUser) {
        if (currentUser == null
                || currentUser.getUser() == null
                || currentUser.getUser().getId() == null) {
            throw new AuthenticationCredentialsNotFoundException(
                    "Authentication is required"
            );
        }
        return currentUser.getUser().getId();
    }
}