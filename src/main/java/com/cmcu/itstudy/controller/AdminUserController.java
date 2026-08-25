package com.cmcu.itstudy.controller;

import com.cmcu.itstudy.dto.admin.user.AdminAssignRoleRequestDto;
import com.cmcu.itstudy.dto.admin.user.AdminUserCreateRequestDto;
import com.cmcu.itstudy.dto.admin.user.AdminUserPageResponseDto;
import com.cmcu.itstudy.dto.admin.user.AdminUserResponseDto;
import com.cmcu.itstudy.dto.admin.user.AdminUserStatusPatchRequestDto;
import com.cmcu.itstudy.dto.admin.user.AdminUserUpdateRequestDto;
import com.cmcu.itstudy.dto.common.ApiResponse;
import com.cmcu.itstudy.dto.common.MessageResponseDto;
import com.cmcu.itstudy.service.contract.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'USER_MODERATOR')")
    public ResponseEntity<ApiResponse<AdminUserPageResponseDto>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search
    ) {
        AdminUserPageResponseDto data = adminUserService.listUsers(page, size, search);
        return ResponseEntity.ok(ApiResponse.success(data, "User list"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER_MODERATOR')")
    public ResponseEntity<ApiResponse<AdminUserResponseDto>> getUser(@PathVariable UUID id) {
        AdminUserResponseDto data = adminUserService.getUser(id);
        return ResponseEntity.ok(ApiResponse.success(data, "User detail"));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'USER_MODERATOR')")
    public ResponseEntity<ApiResponse<AdminUserResponseDto>> createUser(
            @Valid @RequestBody AdminUserCreateRequestDto request
    ) {
        AdminUserResponseDto data = adminUserService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(data, "User created"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER_MODERATOR')")
    public ResponseEntity<ApiResponse<AdminUserResponseDto>> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody AdminUserUpdateRequestDto request
    ) {
        AdminUserResponseDto data = adminUserService.updateUser(id, request);
        return ResponseEntity.ok(ApiResponse.success(data, "User updated"));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER_MODERATOR')")
    public ResponseEntity<ApiResponse<AdminUserResponseDto>> patchStatus(
            @PathVariable UUID id,
            @Valid @RequestBody AdminUserStatusPatchRequestDto request
    ) {
        AdminUserResponseDto data = adminUserService.patchStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success(data, "User status updated"));
    }

    @PostMapping("/{id}/roles")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER_MODERATOR')")
    public ResponseEntity<ApiResponse<AdminUserResponseDto>> assignRole(
            @PathVariable UUID id,
            @Valid @RequestBody AdminAssignRoleRequestDto request
    ) {
        AdminUserResponseDto data = adminUserService.assignRole(id, request);
        return ResponseEntity.ok(ApiResponse.success(data, "Role assigned"));
    }

    @DeleteMapping("/{id}/roles/{roleId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER_MODERATOR')")
    public ResponseEntity<ApiResponse<MessageResponseDto>> removeRole(
            @PathVariable UUID id,
            @PathVariable UUID roleId
    ) {
        adminUserService.removeRole(id, roleId);
        MessageResponseDto msg = MessageResponseDto.builder().message("Role removed").build();
        return ResponseEntity.ok(ApiResponse.success(msg, "Role removed"));
    }
}
