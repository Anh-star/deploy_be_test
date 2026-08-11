package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.dto.auth.UserInfoDto;
import com.cmcu.itstudy.dto.user.UpdateProfileRequestDto;
import com.cmcu.itstudy.entity.User;

import java.util.UUID;

public interface UserProfileService {

    UserInfoDto updateProfile(User currentUser, UpdateProfileRequestDto request);

    UserInfoDto getPublicProfile(UUID userId);
}
