package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.dto.contributor.SellerPayoutProfileResponseDto;
import com.cmcu.itstudy.dto.contributor.SellerPayoutProfileUpdateRequestDto;

import java.util.UUID;

public interface SellerPayoutProfileService {

    SellerPayoutProfileResponseDto getCurrentProfile(UUID sellerId);

    SellerPayoutProfileResponseDto upsertCurrentProfile(
            UUID sellerId,
            SellerPayoutProfileUpdateRequestDto request
    );
}