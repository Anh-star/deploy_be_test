package com.cmcu.itstudy.service.impl;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.cmcu.itstudy.dto.auth.ForgotPasswordRequestDto;
import com.cmcu.itstudy.dto.auth.LoginRequestDto;
import com.cmcu.itstudy.dto.auth.RefreshRequestDto;
import com.cmcu.itstudy.dto.auth.RegisterRequestDto;
import com.cmcu.itstudy.dto.auth.ResetPasswordRequestDto;
import com.cmcu.itstudy.dto.auth.TokenResponseDto;
import com.cmcu.itstudy.dto.auth.UserInfoDto;
import com.cmcu.itstudy.dto.common.MessageResponseDto;
import com.cmcu.itstudy.entity.PasswordResetToken;
import com.cmcu.itstudy.entity.Permission;
import com.cmcu.itstudy.entity.RefreshToken;
import com.cmcu.itstudy.entity.Role;
import com.cmcu.itstudy.entity.RolePermission;
import com.cmcu.itstudy.entity.User;
import com.cmcu.itstudy.entity.UserRole;
import com.cmcu.itstudy.mapper.UserMapper;
import com.cmcu.itstudy.repository.PasswordResetTokenRepository;
import com.cmcu.itstudy.repository.RefreshTokenRepository;
import com.cmcu.itstudy.repository.RoleRepository;
import com.cmcu.itstudy.repository.UserRepository;
import com.cmcu.itstudy.repository.UserRoleRepository;
import com.cmcu.itstudy.service.base.BaseAuthService;
import com.cmcu.itstudy.service.contract.AuthService;
import com.cmcu.itstudy.service.contract.JwtService;

import jakarta.transaction.Transactional;

@Service
public class AuthServiceImpl extends BaseAuthService implements AuthService {

    private static final String ROLE_USER = "USER";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final JwtService jwtService;

    public AuthServiceImpl(
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            JwtService jwtService,
            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder
    ) {
        super(passwordEncoder);
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.jwtService = jwtService;
    }

    @Override
    @Transactional
    public MessageResponseDto register(RegisterRequestDto request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already in use");
        }

        LocalDateTime now = LocalDateTime.now();

        User user = User.builder()
                .email(request.getEmail())
                .password(encodePassword(request.getPassword()))
                .fullName(request.getFullName())
                .status("ACTIVE")
                .emailVerified(false)
                .createdAt(now)
                .updatedAt(now)
                .build();

        user = userRepository.save(user);

        Role defaultRole = roleRepository.findByName(ROLE_USER)
                .orElseThrow(() -> new IllegalArgumentException("Default role USER not found"));

        UserRole.UserRoleId userRoleId = new UserRole.UserRoleId(user.getId(), defaultRole.getId());
        if (!userRoleRepository.existsById(userRoleId)) {
            UserRole userRole = UserRole.builder()
                    .userId(user.getId())
                    .roleId(defaultRole.getId())
                    .createdAt(now)
                    .build();
            userRoleRepository.save(userRole);
        }

        return MessageResponseDto.builder()
                .message("Registered successfully")
                .build();
    }

    @Override
    @Transactional
    public TokenResponseDto login(LoginRequestDto request) {
        User user = userRepository.findByEmailWithRoles(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (!matchesPassword(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new IllegalArgumentException("Tài khoản của bạn đã bị khóa hoặc ngừng hoạt động. Vui lòng liên hệ quản trị viên để được hỗ trợ.");
        }

        List<Role> roles = extractRoles(user);
        // Lọc các vai trò mong muốn ('ADMIN', 'CONTRIBUTOR') để đưa vào token
        List<String> authorizedRoleNames = roles.stream()
    .map(Role::getName)
    .collect(Collectors.toList());
        List<Permission> permissions = extractPermissions(roles);

        String accessToken = jwtService.generateAccessToken(
                user,
                authorizedRoleNames,
                permissions.stream().map(Permission::getName).collect(Collectors.toList())
        );

        String refreshTokenValue = jwtService.generateRefreshToken(user);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime refreshExpiry = now.plusSeconds(jwtService.getRefreshTokenExpirySeconds());

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(refreshTokenValue)
                .expiryDate(refreshExpiry)
                .revoked(false)
                .createdAt(now)
                .build();

        refreshTokenRepository.save(refreshToken);

        UserInfoDto userInfoDto = UserMapper.toUserInfoDto(user, roles, permissions);

        return TokenResponseDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenValue)
                .expiresIn(jwtService.getAccessTokenExpirySeconds())
                .refreshTokenExpiresIn(jwtService.getRefreshTokenExpirySeconds())
                .user(userInfoDto)
                .build();
    }

    @Override
    @Transactional
    public TokenResponseDto refreshToken(RefreshRequestDto request) {
        RefreshToken storedToken = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (Boolean.TRUE.equals(storedToken.getRevoked())) {
            throw new IllegalArgumentException("Refresh token revoked");
        }

        if (storedToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Refresh token expired");
        }

        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        User user = userRepository.findByEmailWithRoles(storedToken.getUser().getEmail())
                .orElseThrow(() -> new IllegalArgumentException("User not found for refresh token"));

        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new IllegalArgumentException("Tài khoản của bạn đã bị khóa hoặc ngừng hoạt động.");
        }

        List<Role> roles = extractRoles(user);
        // Lọc các vai trò mong muốn ('ADMIN', 'CONTRIBUTOR') để đưa vào token
        List<String> authorizedRoleNames = roles.stream()
    .map(Role::getName)
    .collect(Collectors.toList());
        List<Permission> permissions = extractPermissions(roles);

        String newAccessToken = jwtService.generateAccessToken(
                user,
                authorizedRoleNames,
                permissions.stream().map(Permission::getName).collect(Collectors.toList())
        );

        String newRefreshTokenValue = jwtService.generateRefreshToken(user);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime refreshExpiry = now.plusSeconds(jwtService.getRefreshTokenExpirySeconds());

        RefreshToken newRefreshToken = RefreshToken.builder()
                .user(user)
                .token(newRefreshTokenValue)
                .expiryDate(refreshExpiry)
                .revoked(false)
                .createdAt(now)
                .build();

        refreshTokenRepository.save(newRefreshToken);

        UserInfoDto userInfoDto = UserMapper.toUserInfoDto(user, roles, permissions);

        return TokenResponseDto.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshTokenValue)
                .expiresIn(jwtService.getAccessTokenExpirySeconds())
                .refreshTokenExpiresIn(jwtService.getRefreshTokenExpirySeconds())
                .user(userInfoDto)
                .build();
    }

    @Override
    @Transactional
    public MessageResponseDto logout(String refreshTokenValue) {
        Optional<RefreshToken> optional = refreshTokenRepository.findByToken(refreshTokenValue);
        optional.ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });

        return MessageResponseDto.builder()
                .message("Logged out")
                .build();
    }

    @Override
    @Transactional
    public MessageResponseDto logoutAll(User currentUser) {
        List<RefreshToken> tokens = refreshTokenRepository.findByUser(currentUser);
        for (RefreshToken token : tokens) {
            token.setRevoked(true);
        }
        refreshTokenRepository.saveAll(tokens);

        return MessageResponseDto.builder()
                .message("Logged out from all sessions")
                .build();
    }

    @Override
    @Transactional
    public UserInfoDto getCurrentUser(User currentUser) {
        // Fetch the user again to ensure roles are eagerly loaded
        User userWithRoles = userRepository.findByEmailWithRoles(currentUser.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        List<Role> roles = extractRoles(userWithRoles);
        List<Permission> permissions = extractPermissions(roles);
        return UserMapper.toUserInfoDto(userWithRoles, roles, permissions);
    }

    @Override
    @Transactional
    public MessageResponseDto forgotPassword(ForgotPasswordRequestDto request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            // Spec allows email to be optional, but service requires it to send email.
            return MessageResponseDto.builder()
                    .message("If that email exists, a reset link has been sent")
                    .build();
        }

        Optional<User> optionalUser = userRepository.findByEmail(request.getEmail());
        if (optionalUser.isEmpty()) {
            return MessageResponseDto.builder()
                    .message("If that email exists, a reset link has been sent")
                    .build();
        }

        User user = optionalUser.get();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiry = now.plusHours(1);

        String tokenValue = UUID.randomUUID().toString();

        PasswordResetToken token = PasswordResetToken.builder()
                .user(user)
                .token(tokenValue)
                .expiryDate(expiry)
                .used(false)
                .createdAt(now)
                .build();

        passwordResetTokenRepository.save(token);

        // Email sending should be implemented in separate infrastructure/service layer.

        return MessageResponseDto.builder()
                .message("If that email exists, a reset link has been sent")
                .build();
    }

    @Override
    @Transactional
    public MessageResponseDto resetPassword(ResetPasswordRequestDto request) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new IllegalArgumentException("Invalid reset token"));

        if (Boolean.TRUE.equals(resetToken.getUsed())) {
            throw new IllegalArgumentException("Reset token already used");
        }

        if (resetToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Reset token expired");
        }

        User user = resetToken.getUser();
        user.setPassword(encodePassword(request.getNewPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        return MessageResponseDto.builder()
                .message("Password has been reset")
                .build();
    }

    private List<Role> extractRoles(User user) {
        Set<Role> roles = new HashSet<>();
        if (user.getUserRoles() != null) {
            for (UserRole userRole : user.getUserRoles()) {
                if (userRole.getRole() != null) {
                    roles.add(userRole.getRole());
                }
            }
        }
        return roles.stream().toList();
    }

    private List<Permission> extractPermissions(List<Role> roles) {
        Set<Permission> permissions = new HashSet<>();
        for (Role role : roles) {
            if (role.getRolePermissions() != null) {
                for (RolePermission rp : role.getRolePermissions()) {
                    if (rp.getPermission() != null) {
                        permissions.add(rp.getPermission());
                    }
                }
            }
        }
        return permissions.stream().toList();
    }
    @Override
    @Transactional
    public TokenResponseDto loginWithOAuth(
            String email,
            String name,
            String avatar,
            String providerId,
            String provider
    ) {

        LocalDateTime now = LocalDateTime.now();

        String resolvedName = (name != null && !name.isBlank()) ? name.trim() : null;
        String resolvedAvatar = (avatar != null && !avatar.isBlank()) ? avatar.trim() : null;
        String resolvedProvider = (provider != null && !provider.isBlank()) ? provider.trim().toUpperCase() : null;
        String resolvedProviderId = (providerId != null && !providerId.isBlank()) ? providerId.trim() : null;

        User user = userRepository.findByEmailWithRoles(email).orElse(null);

        if (user == null) {
            String fullName = resolvedName != null ? resolvedName : email;

            user = User.builder()
                    .email(email)
                    .password(encodePassword("OAUTH2_USER_" + UUID.randomUUID()))
                    .fullName(fullName)
                    .avatarUrl(resolvedAvatar)
                    .provider(resolvedProvider)
                    .providerId(resolvedProviderId)
                    .status("ACTIVE")
                    .emailVerified(true)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            user = userRepository.save(user);

            Role defaultRole = roleRepository.findByName(ROLE_USER)
                    .orElseThrow(() -> new IllegalArgumentException("Default role USER not found"));

            UserRole userRole = UserRole.builder()
                    .userId(user.getId())
                    .roleId(defaultRole.getId())
                    .createdAt(now)
                    .build();

            userRoleRepository.save(userRole);

            user = userRepository.findByEmailWithRoles(email).orElseThrow();
        } else {
            boolean changed = false;
            if ((user.getFullName() == null || user.getFullName().isBlank()) && resolvedName != null) {
                user.setFullName(resolvedName);
                changed = true;
            }
            if ((user.getAvatarUrl() == null || user.getAvatarUrl().isBlank()) && resolvedAvatar != null) {
                user.setAvatarUrl(resolvedAvatar);
                changed = true;
            }
            if (changed) {
                user.setUpdatedAt(now);
                userRepository.save(user);
                user = userRepository.findByEmailWithRoles(email).orElseThrow();
            }
        }
        List<Role> roles = extractRoles(user);

        List<String> roleNames = roles.stream()
                .map(Role::getName)
                .toList();

        List<Permission> permissions = extractPermissions(roles);

        String accessToken = jwtService.generateAccessToken(
                user,
                roleNames,
                permissions.stream().map(Permission::getName).toList()
        );

        String refreshTokenValue = jwtService.generateRefreshToken(user);

        LocalDateTime refreshExpiry = now.plusSeconds(jwtService.getRefreshTokenExpirySeconds());

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(refreshTokenValue)
                .expiryDate(refreshExpiry)
                .revoked(false)
                .createdAt(now)
                .build();

        refreshTokenRepository.save(refreshToken);

        UserInfoDto userInfoDto = UserMapper.toUserInfoDto(user, roles, permissions);

        return TokenResponseDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenValue)
                .expiresIn(jwtService.getAccessTokenExpirySeconds())
                .refreshTokenExpiresIn(jwtService.getRefreshTokenExpirySeconds())
                .user(userInfoDto)
                .build();
    }
}

