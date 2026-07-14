package com.cmcu.itstudy.controller;

import com.cmcu.itstudy.dto.admin.sellerEarning.SellerEarningReleaseResponseDto;
import com.cmcu.itstudy.dto.common.ApiResponse;
import com.cmcu.itstudy.security.UserDetailsImpl;
import com.cmcu.itstudy.service.contract.SellerEarningReleaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/seller-earnings")
public class AdminSellerEarningController {

    private static final Logger log = LoggerFactory.getLogger(AdminSellerEarningController.class);

    private final SellerEarningReleaseService sellerEarningReleaseService;

    public AdminSellerEarningController(SellerEarningReleaseService sellerEarningReleaseService) {
        this.sellerEarningReleaseService = sellerEarningReleaseService;
    }

    @PostMapping("/{earningId}/release")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SellerEarningReleaseResponseDto>> release(
            @PathVariable UUID earningId,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        UUID adminUserId = currentUser.getUser().getId();

        log.info("Admin release requested: adminUserId={}, earningId={}", adminUserId, earningId);

        boolean released = sellerEarningReleaseService.releaseIfDue(earningId);

        String message = released
                ? "Seller earning released successfully"
                : "Seller earning is not due or is no longer pending";

        SellerEarningReleaseResponseDto body = SellerEarningReleaseResponseDto.builder()
                .earningId(earningId)
                .released(released)
                .message(message)
                .build();

        log.info("Admin release completed: adminUserId={}, earningId={}, released={}",
                adminUserId, earningId, released);

        return ResponseEntity.ok(ApiResponse.success(body, message));
    }
}