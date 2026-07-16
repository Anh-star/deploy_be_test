package com.cmcu.itstudy.controller;

import com.cmcu.itstudy.dto.common.ApiResponse;
import com.cmcu.itstudy.dto.contributor.ContributorWithdrawalCreateRequestDto;
import com.cmcu.itstudy.dto.contributor.ContributorWithdrawalResponseDto;
import com.cmcu.itstudy.security.UserDetailsImpl;
import com.cmcu.itstudy.service.contract.ContributorWithdrawalService;
import com.cmcu.itstudy.service.dto.ContributorWithdrawalCreateResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/contributor/withdrawals")
@RequiredArgsConstructor
public class ContributorWithdrawalController {

    private final ContributorWithdrawalService
            contributorWithdrawalService;

    @PostMapping
    @PreAuthorize("hasRole('CONTRIBUTOR')")
    public ResponseEntity<
            ApiResponse<ContributorWithdrawalResponseDto>
    > createWithdrawal(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @Valid
            @RequestBody
            ContributorWithdrawalCreateRequestDto request
    ) {
        if (currentUser == null
                || currentUser.getUser() == null
                || currentUser.getUser().getId() == null) {
            throw new AuthenticationCredentialsNotFoundException(
                    "Authentication is required"
            );
        }

        UUID sellerId = currentUser.getUser().getId();

        ContributorWithdrawalCreateResult result =
                contributorWithdrawalService.createWithdrawal(
                        sellerId,
                        request
                );

        HttpStatus status = result.isCreated()
                ? HttpStatus.CREATED
                : HttpStatus.OK;

        String message = result.isCreated()
                ? "Withdrawal request created successfully"
                : "Withdrawal request already exists";

        ApiResponse<ContributorWithdrawalResponseDto> body =
                ApiResponse.success(
                        result.getData(),
                        message
                );

        return ResponseEntity
                .status(status)
                .body(body);
    }
}
