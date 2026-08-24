package com.cmcu.itstudy.controller;

import com.cmcu.itstudy.dto.common.ApiResponse;
import com.cmcu.itstudy.dto.common.MessageResponseDto;
import com.cmcu.itstudy.dto.contributor.ContributorProfileDto;
import com.cmcu.itstudy.dto.contributor.ContributorRegistrationRequestDto;
import com.cmcu.itstudy.dto.contributor.ContributorStatusDto;
import com.cmcu.itstudy.security.UserDetailsImpl;
import com.cmcu.itstudy.service.contract.ContributorService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/contributor")
public class ContributorController {

    private final ContributorService contributorService;

    public ContributorController(ContributorService contributorService) {
        this.contributorService = contributorService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<MessageResponseDto>> registerContributor(
            @Valid @RequestBody ContributorRegistrationRequestDto request,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        if (currentUser == null) {
            throw new RuntimeException("Bạn cần đăng nhập để thực hiện chức năng này.");
        }

        UUID userId = currentUser.getUser().getId();
        contributorService.registerContributor(request, userId);

        MessageResponseDto response = MessageResponseDto.builder()
                .message("Yêu cầu đăng ký Contributor đã được gửi thành công.")
                .build();

        return ResponseEntity.ok(ApiResponse.success(response, response.getMessage()));
    }

    @GetMapping("/registration-status")
    public ResponseEntity<ApiResponse<ContributorStatusDto>> getRegistrationStatus(
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        if (currentUser == null) {
            throw new RuntimeException("Bạn cần đăng nhập để thực hiện chức năng này.");
        }

        UUID userId = currentUser.getUser().getId();
        ContributorStatusDto status = contributorService.getRegistrationStatus(userId);

        return ResponseEntity.ok(ApiResponse.success(status, "Lấy trạng thái đăng ký thành công."));
    }

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<ContributorProfileDto>> getContributorProfile(
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        if (currentUser == null) {
            throw new RuntimeException("Bạn cần đăng nhập để xem hồ sơ.");
        }

        UUID userId = currentUser.getUser().getId();
        ContributorProfileDto profile = contributorService.getContributorProfile(userId);

        return ResponseEntity.ok(ApiResponse.success(profile, "Lấy hồ sơ contributor thành công."));
    }
}
