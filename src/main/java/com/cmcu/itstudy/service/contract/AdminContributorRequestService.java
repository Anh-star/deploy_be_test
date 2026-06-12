package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.enums.ContributorRequestStatus;
import java.util.Map;
import java.util.UUID;

public interface AdminContributorRequestService {
    void updateContributorRequestStatus(UUID requestId, ContributorRequestStatus status, String rejectionReason, Map<String, String> requestedFields);
}
