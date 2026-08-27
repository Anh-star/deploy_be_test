package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.dto.admin.user.AdminAssignRoleRequestDto;
import com.cmcu.itstudy.dto.admin.user.AdminUserCreateRequestDto;
import com.cmcu.itstudy.dto.admin.user.AdminUserPageResponseDto;
import com.cmcu.itstudy.dto.admin.user.AdminUserResponseDto;
import com.cmcu.itstudy.dto.admin.user.AdminUserStatusPatchRequestDto;
import com.cmcu.itstudy.dto.admin.user.AdminUserUpdateRequestDto;

import java.util.UUID;

public interface AdminUserService {

    AdminUserPageResponseDto listUsers(int page, int size, String search, String status, java.time.LocalDateTime startDate, java.time.LocalDateTime endDate);

    default AdminUserPageResponseDto listUsers(int page, int size, String search) {
        return listUsers(page, size, search, null, null, null);
    }

    AdminUserResponseDto getUser(UUID id);

    AdminUserResponseDto createUser(AdminUserCreateRequestDto request);

    AdminUserResponseDto updateUser(UUID id, AdminUserUpdateRequestDto request);

    AdminUserResponseDto patchStatus(UUID id, AdminUserStatusPatchRequestDto request);

    AdminUserResponseDto assignRole(UUID userId, AdminAssignRoleRequestDto request);

    void removeRole(UUID userId, UUID roleId);
}
