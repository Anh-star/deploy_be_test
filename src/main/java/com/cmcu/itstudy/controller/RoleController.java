package com.cmcu.itstudy.controller;

import com.cmcu.itstudy.dto.admin.permission.AdminPermissionResponseDto;
import com.cmcu.itstudy.dto.admin.role.AdminRoleCreateRequestDto;
import com.cmcu.itstudy.dto.admin.role.AdminRolePageResponseDto;
import com.cmcu.itstudy.dto.admin.role.AdminRoleResponseDto;
import com.cmcu.itstudy.dto.admin.role.AdminRoleStatusPatchRequestDto;
import com.cmcu.itstudy.dto.admin.role.AdminRoleUpdateRequestDto;
import com.cmcu.itstudy.dto.admin.role.RolePermissionIdsRequestDto;
import com.cmcu.itstudy.dto.common.ApiResponse;
import com.cmcu.itstudy.dto.common.MessageResponseDto;
import com.cmcu.itstudy.service.contract.RoleService;
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

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdminRolePageResponseDto>> listRoles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        AdminRolePageResponseDto data = roleService.listRoles(page, size);
        return ResponseEntity.ok(ApiResponse.success(data, "Role list"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdminRoleResponseDto>> getRole(@PathVariable UUID id) {
        AdminRoleResponseDto data = roleService.getRole(id);
        return ResponseEntity.ok(ApiResponse.success(data, "Role detail"));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdminRoleResponseDto>> createRole(
            @Valid @RequestBody AdminRoleCreateRequestDto request
    ) {
        AdminRoleResponseDto data = roleService.createRole(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(data, "Role created"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdminRoleResponseDto>> updateRole(
            @PathVariable UUID id,
            @Valid @RequestBody AdminRoleUpdateRequestDto request
    ) {
        AdminRoleResponseDto data = roleService.updateRole(id, request);
        return ResponseEntity.ok(ApiResponse.success(data, "Role updated"));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdminRoleResponseDto>> patchStatus(
            @PathVariable UUID id,
            @Valid @RequestBody AdminRoleStatusPatchRequestDto request
    ) {
        AdminRoleResponseDto data = roleService.patchStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success(data, "Role status updated"));
    }

    @GetMapping("/{id}/permissions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<AdminPermissionResponseDto>>> getRolePermissions(@PathVariable UUID id) {
        List<AdminPermissionResponseDto> data = roleService.getRolePermissions(id);
        return ResponseEntity.ok(ApiResponse.success(data, "Role permissions"));
    }

    @PostMapping("/{id}/permissions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MessageResponseDto>> addPermissionsToRole(
            @PathVariable UUID id,
            @RequestBody(required = false) RolePermissionIdsRequestDto body
    ) {
        List<UUID> permissionIds = body != null && body.getPermissionIds() != null
                ? body.getPermissionIds()
                : Collections.emptyList();
        roleService.addPermissionsToRole(id, permissionIds);
        MessageResponseDto msg = MessageResponseDto.builder().message("OK").build();
        return ResponseEntity.ok(ApiResponse.success(msg, "Permissions assigned"));
    }

    @DeleteMapping("/{id}/permissions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MessageResponseDto>> removePermissionsFromRole(
            @PathVariable UUID id,
            @RequestBody(required = false) RolePermissionIdsRequestDto body
    ) {
        List<UUID> permissionIds = body != null && body.getPermissionIds() != null
                ? body.getPermissionIds()
                : Collections.emptyList();
        roleService.removePermissionsFromRole(id, permissionIds);
        MessageResponseDto msg = MessageResponseDto.builder().message("OK").build();
        return ResponseEntity.ok(ApiResponse.success(msg, "Permissions removed"));
    }
}
