package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.dto.admin.user.AdminAssignRoleRequestDto;
import com.cmcu.itstudy.dto.admin.user.AdminUserCreateRequestDto;
import com.cmcu.itstudy.dto.admin.user.AdminUserPageResponseDto;
import com.cmcu.itstudy.dto.admin.user.AdminUserResponseDto;
import com.cmcu.itstudy.dto.admin.user.AdminUserStatusPatchRequestDto;
import com.cmcu.itstudy.dto.admin.user.AdminUserUpdateRequestDto;
import com.cmcu.itstudy.entity.Role;
import com.cmcu.itstudy.entity.User;
import com.cmcu.itstudy.entity.UserRole;
import com.cmcu.itstudy.mapper.AdminUserMapper;
import com.cmcu.itstudy.repository.RoleRepository;
import com.cmcu.itstudy.repository.UserRepository;
import com.cmcu.itstudy.repository.UserRoleRepository;
import com.cmcu.itstudy.service.base.BaseAuthService;
import com.cmcu.itstudy.service.contract.AdminUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AdminUserServiceImpl extends BaseAuthService implements AdminUserService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final com.cmcu.itstudy.repository.CommunityPostRepository communityPostRepository;
    private final com.cmcu.itstudy.repository.DocumentRepository documentRepository;
    private final com.cmcu.itstudy.repository.RefreshTokenRepository refreshTokenRepository;

    public AdminUserServiceImpl(
            PasswordEncoder passwordEncoder,
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            com.cmcu.itstudy.repository.CommunityPostRepository communityPostRepository,
            com.cmcu.itstudy.repository.DocumentRepository documentRepository,
            com.cmcu.itstudy.repository.RefreshTokenRepository refreshTokenRepository
    ) {
        super(passwordEncoder);
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.communityPostRepository = communityPostRepository;
        this.documentRepository = documentRepository;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUserPageResponseDto listUsers(int page, int size, String search) {
        int p = Math.max(0, page);
        int s = size > 0 ? Math.min(size, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;
        String q = (search != null && !search.isBlank()) ? search.trim() : null;
        Pageable pageable = PageRequest.of(p, s, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> result = userRepository.searchForAdmin(q, pageable);
        List<AdminUserResponseDto> content = result.getContent().stream()
                .map(AdminUserMapper::toResponseDto)
                .collect(Collectors.toList());
        return AdminUserPageResponseDto.builder()
                .content(content)
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUserResponseDto getUser(UUID id) {
        User user = userRepository.findByIdWithRoles(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return AdminUserMapper.toResponseDto(user);
    }

    @Override
    @Transactional
    public AdminUserResponseDto createUser(AdminUserCreateRequestDto request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already in use");
        }
        LocalDateTime now = LocalDateTime.now();
        String status = (request.getStatus() != null && !request.getStatus().isBlank())
                ? request.getStatus().trim()
                : "ACTIVE";
        Boolean verified = request.getEmailVerified() != null ? request.getEmailVerified() : Boolean.FALSE;
        User user = User.builder()
                .email(request.getEmail().trim())
                .password(encodePassword(request.getPassword()))
                .fullName(request.getFullName() != null ? request.getFullName().trim() : null)
                .avatarUrl(request.getAvatar())
                .status(status)
                .emailVerified(verified)
                .createdAt(now)
                .updatedAt(now)
                .build();
        user = userRepository.save(user);
        User reloaded = userRepository.findByIdWithRoles(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return AdminUserMapper.toResponseDto(reloaded);
    }

    @Override
    @Transactional
    public AdminUserResponseDto updateUser(UUID id, AdminUserUpdateRequestDto request) {
        boolean hasChange = request.getEmail() != null
                || request.getFullName() != null
                || request.getAvatar() != null
                || request.getEmailVerified() != null
                || (request.getPassword() != null && !request.getPassword().isBlank());
        if (!hasChange) {
            throw new IllegalArgumentException("No fields to update");
        }

        User user = userRepository.findByIdWithRoles(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            String newEmail = request.getEmail().trim();
            if (!newEmail.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmail(newEmail)) {
                throw new IllegalArgumentException("Email already in use");
            }
            user.setEmail(newEmail);
        }
        if (request.getFullName() != null) {
            user.setFullName(request.getFullName().isBlank() ? null : request.getFullName().trim());
        }
        if (request.getAvatar() != null) {
            user.setAvatarUrl(request.getAvatar().isBlank() ? null : request.getAvatar().trim());
        }
        if (request.getEmailVerified() != null) {
            user.setEmailVerified(request.getEmailVerified());
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(encodePassword(request.getPassword()));
        }
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        User reloaded = userRepository.findByIdWithRoles(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return AdminUserMapper.toResponseDto(reloaded);
    }

    @Override
    @Transactional
    public AdminUserResponseDto patchStatus(UUID id, AdminUserStatusPatchRequestDto request) {
        User user = userRepository.findByIdWithRoles(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        String newStatus = request.getStatus().trim().toUpperCase();
        user.setStatus(newStatus);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        if ("LOCKED".equalsIgnoreCase(newStatus) || "BANNED".equalsIgnoreCase(newStatus) || "INACTIVE".equalsIgnoreCase(newStatus)) {
            // Revoke all tokens
            refreshTokenRepository.revokeAllByUserId(id);
            // Hide all community posts by this user
            communityPostRepository.hideAllByAuthorId(id);
            // Hide all documents by this user
            documentRepository.hideAllByCreatedById(id);
        } else if ("ACTIVE".equalsIgnoreCase(newStatus)) {
            // Unhide all community posts by this user
            communityPostRepository.unhideAllByAuthorId(id);
            // Unhide all documents by this user
            documentRepository.unhideAllByCreatedById(id);
        }

        User reloaded = userRepository.findByIdWithRoles(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return AdminUserMapper.toResponseDto(reloaded);
    }

    @Override
    @Transactional
    public AdminUserResponseDto assignRole(UUID userId, AdminAssignRoleRequestDto request) {
        userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));
        if (Boolean.FALSE.equals(role.getActive())) {
            throw new IllegalArgumentException("Role is inactive");
        }

        UserRole.UserRoleId compositeId = new UserRole.UserRoleId(userId, role.getId());
        if (userRoleRepository.existsById(compositeId)) {
            return getUser(userId);
        }

        LocalDateTime now = LocalDateTime.now();
        UserRole userRole = UserRole.builder()
                .userId(userId)
                .roleId(role.getId())
                .createdAt(now)
                .build();
        userRoleRepository.save(userRole);
        return getUser(userId);
    }

    @Override
    @Transactional
    public void removeRole(UUID userId, UUID roleId) {
        UserRole.UserRoleId compositeId = new UserRole.UserRoleId(userId, roleId);
        if (!userRoleRepository.existsById(compositeId)) {
            throw new IllegalArgumentException("User does not have this role");
        }
        userRoleRepository.deleteById(compositeId);
    }
}
