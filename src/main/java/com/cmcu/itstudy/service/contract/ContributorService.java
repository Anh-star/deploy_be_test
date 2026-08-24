package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.dto.contributor.ContributorProfileDto;
import com.cmcu.itstudy.dto.contributor.ContributorRegistrationRequestDto;
import com.cmcu.itstudy.dto.contributor.ContributorStatusDto;
import java.util.UUID;

public interface ContributorService {
    void registerContributor(ContributorRegistrationRequestDto request, UUID userId);
    ContributorStatusDto getRegistrationStatus(UUID userId);
    ContributorProfileDto getContributorProfile(UUID userId);
}
