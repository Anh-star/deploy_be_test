package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.entity.ContributorRequest;
import com.cmcu.itstudy.entity.Role;
import com.cmcu.itstudy.entity.User;
import com.cmcu.itstudy.entity.UserRole;
import com.cmcu.itstudy.enums.ContributorRequestStatus;
import com.cmcu.itstudy.enums.NotificationType;
import com.cmcu.itstudy.enums.RoleEnum;
import com.cmcu.itstudy.repository.ContributorRequestRepository;
import com.cmcu.itstudy.repository.RoleRepository;
import com.cmcu.itstudy.repository.UserRepository;
import com.cmcu.itstudy.repository.UserRoleRepository;
import com.cmcu.itstudy.service.contract.AdminContributorRequestService;
import com.cmcu.itstudy.service.contract.NotificationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminContributorRequestServiceImpl implements AdminContributorRequestService {

    private final ContributorRequestRepository contributorRequestRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Số lần tối đa admin được yêu cầu bổ sung. */
    private static final int MAX_SUPPLEMENT_COUNT = 3;

    public AdminContributorRequestServiceImpl(
            ContributorRequestRepository contributorRequestRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            NotificationService notificationService) {
        this.contributorRequestRepository = contributorRequestRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional
    public void updateContributorRequestStatus(UUID requestId, ContributorRequestStatus newStatus, String rejectionReason, Map<String, String> requestedFields) {
        ContributorRequest request = contributorRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Contributor request not found"));

        request.setStatus(newStatus);
        request.setUpdatedAt(LocalDateTime.now());

        if (newStatus == ContributorRequestStatus.NEED_INFO) {
            // Kiểm tra giới hạn yêu cầu bổ sung
            if (request.getSupplementCount() >= MAX_SUPPLEMENT_COUNT) {
                throw new RuntimeException("Đã đạt giới hạn " + MAX_SUPPLEMENT_COUNT + " lần yêu cầu bổ sung cho yêu cầu này.");
            }
            request.setSupplementCount(request.getSupplementCount() + 1);
            request.setRejectionReason(rejectionReason);
            // Lưu map trường -> lý do dưới dạng JSON
            if (requestedFields != null && !requestedFields.isEmpty()) {
                try {
                    request.setRequestedFields(objectMapper.writeValueAsString(requestedFields));
                } catch (JsonProcessingException e) {
                    throw new RuntimeException("Lỗi khi lưu danh sách trường cần bổ sung.", e);
                }
            } else {
                request.setRequestedFields(null);
            }
        } else if (newStatus == ContributorRequestStatus.REJECTED) {
            request.setRejectionReason(rejectionReason);
            request.setRequestedFields(null);
        } else {
            request.setRejectionReason(null);
            request.setRequestedFields(null);
        }

        contributorRequestRepository.save(request);

        if (newStatus == ContributorRequestStatus.APPROVED) {
            User user = userRepository.findById(request.getUser().getId())
                    .orElseThrow(() -> new RuntimeException("User not found for request"));
            UUID uid = user.getId();

            roleRepository.findByName(RoleEnum.USER.name()).ifPresent(userRole -> {
                UserRole.UserRoleId userComposite = new UserRole.UserRoleId(uid, userRole.getId());
                if (userRoleRepository.existsById(userComposite)) {
                    userRoleRepository.deleteById(userComposite);
                }
            });

            Role contributorRole = roleRepository.findByName(RoleEnum.CONTRIBUTOR.name())
                    .orElseThrow(() -> new RuntimeException("Contributor role not found"));
            UserRole.UserRoleId contributorComposite = new UserRole.UserRoleId(uid, contributorRole.getId());
            if (!userRoleRepository.existsById(contributorComposite)) {
                UserRole newUserRole = UserRole.builder()
                        .userId(uid)
                        .roleId(contributorRole.getId())
                        .user(user)
                        .role(contributorRole)
                        .createdAt(LocalDateTime.now())
                        .build();
                userRoleRepository.save(newUserRole);
            }

            notificationService.createAndPush(
                    user.getId(),
                    null,
                    NotificationType.CONTRIBUTOR_APPROVED,
                    request.getId().toString(),
                    "CONTRIBUTOR_REQUEST",
                    "Hồ sơ người đóng góp (Contributor) của bạn đã được phê duyệt thành công!"
            );
        } else if (newStatus == ContributorRequestStatus.REJECTED) {
            notificationService.createAndPush(
                    request.getUser().getId(),
                    null,
                    NotificationType.CONTRIBUTOR_REJECTED,
                    request.getId().toString(),
                    "CONTRIBUTOR_REQUEST",
                    "Hồ sơ người đóng góp (Contributor) của bạn đã bị từ chối." + (rejectionReason != null ? " Lý do: " + rejectionReason : "")
            );
        }
    }
}
