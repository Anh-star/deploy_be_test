package com.cmcu.itstudy.controller;

import com.cmcu.itstudy.dto.auth.ForgotPasswordRequestDto;
import com.cmcu.itstudy.dto.auth.LoginRequestDto;
import com.cmcu.itstudy.dto.auth.RefreshRequestDto;
import com.cmcu.itstudy.dto.auth.RegisterRequestDto;
import com.cmcu.itstudy.dto.auth.ResetPasswordRequestDto;
import com.cmcu.itstudy.dto.auth.TokenResponseDto;
import com.cmcu.itstudy.dto.auth.UserInfoDto;
import com.cmcu.itstudy.dto.common.ApiResponse;
import com.cmcu.itstudy.dto.common.MessageResponseDto;
import com.cmcu.itstudy.entity.User;
import com.cmcu.itstudy.security.UserDetailsImpl;
import com.cmcu.itstudy.service.contract.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<MessageResponseDto>> register(
            @Valid @RequestBody RegisterRequestDto request
    ) {
        MessageResponseDto result = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(result, result.getMessage()));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponseDto>> login(
            @Valid @RequestBody LoginRequestDto request
    ) {
        TokenResponseDto tokenResponse = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(tokenResponse, "Login successful"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponseDto>> refresh(
            @Valid @RequestBody RefreshRequestDto request
    ) {
        TokenResponseDto tokenResponse = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success(tokenResponse, "Token refreshed"));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<MessageResponseDto>> logout(
            @RequestHeader(name = "X-Refresh-Token", required = false) String refreshToken
    ) {
        MessageResponseDto result = authService.logout(refreshToken);
        return ResponseEntity.ok(ApiResponse.success(result, result.getMessage()));
    }

    @PostMapping("/logout-all")
    public ResponseEntity<ApiResponse<MessageResponseDto>> logoutAll(
            @AuthenticationPrincipal UserDetailsImpl currentUserDetails
    ) {
        User currentUser = currentUserDetails.getUser();
        MessageResponseDto result = authService.logoutAll(currentUser);
        return ResponseEntity.ok(ApiResponse.success(result, result.getMessage()));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserInfoDto>> me(
            @AuthenticationPrincipal UserDetailsImpl currentUserDetails
    ) {
        if (currentUserDetails == null) {
            throw new AccessDeniedException("Authentication required");
        }
        User currentUser = currentUserDetails.getUser();
        UserInfoDto userInfoDto = authService.getCurrentUser(currentUser);
        return ResponseEntity.ok(ApiResponse.success(userInfoDto, "Current user"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<MessageResponseDto>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequestDto request
    ) {
        MessageResponseDto result = authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success(result, result.getMessage()));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<MessageResponseDto>> resetPassword(
            @Valid @RequestBody ResetPasswordRequestDto request
    ) {
        MessageResponseDto result = authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success(result, result.getMessage()));
    }
}

