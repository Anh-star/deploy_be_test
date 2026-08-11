package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.dto.auth.UserInfoDto;
import com.cmcu.itstudy.dto.user.UpdateProfileRequestDto;
import com.cmcu.itstudy.entity.User;
import com.cmcu.itstudy.repository.UserRepository;
import com.cmcu.itstudy.service.contract.AuthService;
import com.cmcu.itstudy.service.contract.UserProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class UserProfileServiceImpl implements UserProfileService {

    private final UserRepository userRepository;
    private final AuthService authService;

    public UserProfileServiceImpl(UserRepository userRepository, AuthService authService) {
        this.userRepository = userRepository;
        this.authService = authService;
    }

    @Override
    @Transactional
    public UserInfoDto updateProfile(User currentUser, UpdateProfileRequestDto request) {
        boolean any = request.getFullName() != null
                || request.getPhone() != null
                || request.getBio() != null
                || request.getAvatarUrl() != null;
        if (!any) {
            throw new IllegalArgumentException("Không có trường nào để cập nhật.");
        }

        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName().isBlank() ? null : request.getFullName().trim());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone().isBlank() ? null : request.getPhone().trim());
        }
        if (request.getBio() != null) {
            user.setBio(request.getBio().isBlank() ? null : request.getBio().trim());
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl().isBlank() ? null : request.getAvatarUrl().trim());
        }

        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        return authService.getCurrentUser(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserInfoDto getPublicProfile(java.util.UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new java.util.NoSuchElementException("User not found"));
        return authService.getCurrentUser(user);
    }
}
