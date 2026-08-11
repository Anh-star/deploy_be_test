package com.cmcu.itstudy.controller;

import com.cmcu.itstudy.dto.auth.UserInfoDto;
import com.cmcu.itstudy.dto.common.ApiResponse;
import com.cmcu.itstudy.dto.user.UpdateProfileRequestDto;
import com.cmcu.itstudy.dto.user.UserDashboardDto;
import com.cmcu.itstudy.entity.User;
import com.cmcu.itstudy.security.UserDetailsImpl;
import com.cmcu.itstudy.service.contract.UserDashboardService;
import com.cmcu.itstudy.service.contract.UserProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserProfileService userProfileService;
    private final UserDashboardService userDashboardService;

    public UserController(
            UserProfileService userProfileService,
            UserDashboardService userDashboardService
    ) {
        this.userProfileService = userProfileService;
        this.userDashboardService = userDashboardService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<UserDashboardDto>> getDashboard(
            @AuthenticationPrincipal UserDetailsImpl principal
    ) {
        User currentUser = principal.getUser();
        UserDashboardDto data = userDashboardService.getDashboardForUser(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(data, "OK"));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserInfoDto>> updateProfile(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @Valid @RequestBody UpdateProfileRequestDto request
    ) {
        User currentUser = principal.getUser();
        UserInfoDto data = userProfileService.updateProfile(currentUser, request);
        return ResponseEntity.ok(ApiResponse.success(data, "Cập nhật hồ sơ thành công"));
    }

    @GetMapping("/{userId}/public-profile")
    public ResponseEntity<ApiResponse<UserInfoDto>> getPublicProfile(
            @org.springframework.web.bind.annotation.PathVariable java.util.UUID userId
    ) {
        UserInfoDto data = userProfileService.getPublicProfile(userId);
        return ResponseEntity.ok(ApiResponse.success(data, "OK"));
    }
}
