package com.cmcu.itstudy.controller;

import com.cmcu.itstudy.dto.common.ApiResponse;
import com.cmcu.itstudy.dto.contributor.SellerPayoutProfileResponseDto;
import com.cmcu.itstudy.dto.contributor.SellerPayoutProfileUpdateRequestDto;
import com.cmcu.itstudy.security.UserDetailsImpl;
import com.cmcu.itstudy.service.contract.SellerPayoutProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/contributor/payout-profile")
@RequiredArgsConstructor
public class ContributorPayoutProfileController {

    private final SellerPayoutProfileService sellerPayoutProfileService;

    @GetMapping
    @PreAuthorize("hasRole('CONTRIBUTOR')")
    public ResponseEntity<ApiResponse<SellerPayoutProfileResponseDto>> getProfile(
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        if (currentUser == null) {
            throw new RuntimeException("Bạn cần đăng nhập để thực hiện chức năng này.");
        }

        UUID sellerId = currentUser.getUser().getId();
        SellerPayoutProfileResponseDto data =
                sellerPayoutProfileService.getCurrentProfile(sellerId);

        String message = data.isConfigured()
                ? "Payout profile retrieved successfully"
                : "Payout profile is not configured";

        return ResponseEntity.ok(ApiResponse.success(data, message));
    }

    @PutMapping
    @PreAuthorize("hasRole('CONTRIBUTOR')")
    public ResponseEntity<ApiResponse<SellerPayoutProfileResponseDto>> upsertProfile(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @Valid @RequestBody SellerPayoutProfileUpdateRequestDto request
    ) {
        if (currentUser == null) {
            throw new RuntimeException("Bạn cần đăng nhập để thực hiện chức năng này.");
        }

        UUID sellerId = currentUser.getUser().getId();
        SellerPayoutProfileResponseDto data =
                sellerPayoutProfileService.upsertCurrentProfile(sellerId, request);

        return ResponseEntity.ok(
                ApiResponse.success(data, "Payout profile saved successfully")
        );
    }
}