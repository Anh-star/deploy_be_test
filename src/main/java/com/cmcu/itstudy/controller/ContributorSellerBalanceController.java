package com.cmcu.itstudy.controller;

import com.cmcu.itstudy.dto.common.ApiResponse;
import com.cmcu.itstudy.dto.contributor.SellerBalanceResponseDto;
import com.cmcu.itstudy.security.UserDetailsImpl;
import com.cmcu.itstudy.service.contract.SellerBalanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/contributor")
@RequiredArgsConstructor
public class ContributorSellerBalanceController {

    private final SellerBalanceService sellerBalanceService;

    @GetMapping("/balance")
    @PreAuthorize("hasRole('CONTRIBUTOR')")
    public ResponseEntity<ApiResponse<SellerBalanceResponseDto>> getBalance(
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        UUID sellerId = requireAuthenticatedUserId(currentUser);

        SellerBalanceResponseDto data =
                sellerBalanceService.getContributorBalance(sellerId);

        return ResponseEntity.ok(
                ApiResponse.success(data, "Seller balance retrieved successfully")
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