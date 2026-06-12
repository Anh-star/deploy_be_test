package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.dto.contributor.CertificateDto;
import com.cmcu.itstudy.dto.contributor.ContributorRegistrationRequestDto;
import com.cmcu.itstudy.dto.contributor.ContributorStatusDto;
import com.cmcu.itstudy.entity.ContributorCertificate;
import com.cmcu.itstudy.entity.ContributorRequest;
import com.cmcu.itstudy.entity.User;
import com.cmcu.itstudy.enums.ContributorRequestStatus;
import com.cmcu.itstudy.repository.ContributorRequestRepository;
import com.cmcu.itstudy.repository.UserRepository;
import com.cmcu.itstudy.service.contract.ContributorService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ContributorServiceImpl implements ContributorService {

    private final ContributorRequestRepository contributorRequestRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ContributorServiceImpl(
            ContributorRequestRepository contributorRequestRepository,
            UserRepository userRepository) {
        this.contributorRequestRepository = contributorRequestRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public void registerContributor(ContributorRegistrationRequestDto request, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (contributorRequestRepository.existsByUserAndStatus(user, ContributorRequestStatus.PENDING)) {
            throw new RuntimeException("Bạn đã có một yêu cầu đang chờ duyệt.");
        }

        Optional<ContributorRequest> latestOpt = contributorRequestRepository.findFirstByUserOrderByCreatedAtDesc(user);
        LocalDateTime now = LocalDateTime.now();

        if (latestOpt.isEmpty()) {
            ContributorRequest contributorRequest = ContributorRequest.builder()
                    .user(user)
                    .portfolioLink(request.getPortfolioLink())
                    .experience(request.getExperience())
                    .status(ContributorRequestStatus.PENDING)
                    .submissionCount(1)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            applyCertificates(contributorRequest, request);
            contributorRequestRepository.save(contributorRequest);
            return;
        }

        ContributorRequest latest = latestOpt.get();
        ContributorRequestStatus st = latest.getStatus();

        if (st == ContributorRequestStatus.APPROVED) {
            throw new RuntimeException("Yêu cầu Contributor của bạn đã được phê duyệt.");
        }

        if (st == ContributorRequestStatus.REJECTED) {
            if (latest.getSubmissionCount() >= 2) {
                throw new RuntimeException("Bạn đã hết số lần gửi yêu cầu Contributor.");
            }
            latest.setSubmissionCount(latest.getSubmissionCount() + 1);
            latest.setStatus(ContributorRequestStatus.PENDING);
            latest.setRejectionReason(null);
            latest.setRequestedFields(null);
            latest.setPortfolioLink(request.getPortfolioLink());
            latest.setExperience(request.getExperience());
            latest.setUpdatedAt(now);
            applyCertificates(latest, request);
            contributorRequestRepository.save(latest);
            return;
        }

        if (st == ContributorRequestStatus.NEED_INFO) {
            latest.setStatus(ContributorRequestStatus.PENDING);
            latest.setRejectionReason(null);
            latest.setRequestedFields(null);
            latest.setPortfolioLink(request.getPortfolioLink());
            latest.setExperience(request.getExperience());
            latest.setUpdatedAt(now);
            applyCertificates(latest, request);
            contributorRequestRepository.save(latest);
            return;
        }

        throw new RuntimeException("Bạn đã có một yêu cầu đang chờ duyệt.");
    }

    private void applyCertificates(ContributorRequest contributorRequest, ContributorRegistrationRequestDto request) {
        if (!request.getCertificates().isEmpty()) {
            contributorRequest.setCertificateName(request.getCertificates().get(0).getCertificateName());
            contributorRequest.setCertificateUrl(request.getCertificates().get(0).getUrl());
        } else {
            contributorRequest.setCertificateName(null);
            contributorRequest.setCertificateUrl(null);
        }

        contributorRequest.getCertificates().clear();
        List<ContributorCertificate> certificates = request.getCertificates().stream()
                .map(certDto -> ContributorCertificate.builder()
                        .request(contributorRequest)
                        .certificateUrl(certDto.getUrl())
                        .certificateName(certDto.getCertificateName())
                        .build())
                .collect(Collectors.toList());
        contributorRequest.getCertificates().addAll(certificates);
    }

    @Override
    public ContributorStatusDto getRegistrationStatus(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Optional<ContributorRequest> latestRequest = contributorRequestRepository.findFirstByUserOrderByCreatedAtDesc(user);

        if (latestRequest.isEmpty()) {
            return null;
        }

        ContributorRequest request = latestRequest.get();

        // Parse requestedFields từ JSON
        Map<String, String> parsedFields = Collections.emptyMap();
        if (request.getRequestedFields() != null && !request.getRequestedFields().isBlank()) {
            try {
                parsedFields = objectMapper.readValue(request.getRequestedFields(), new TypeReference<Map<String, String>>() {});
            } catch (Exception e) {
                System.err.println("Error parsing requestedFields: " + e.getMessage());
            }
        }

        List<CertificateDto> certificatesDto = Collections.emptyList();
        if (request.getCertificates() != null && !request.getCertificates().isEmpty()) {
            certificatesDto = request.getCertificates().stream()
                    .map(cert -> CertificateDto.builder()
                            .url(cert.getCertificateUrl())
                            .certificateName(cert.getCertificateName())
                            .build())
                    .collect(Collectors.toList());
        }

        return ContributorStatusDto.builder()
                .status(request.getStatus())
                .rejectionReason(request.getRejectionReason())
                .createdAt(request.getCreatedAt())
                .updatedAt(request.getUpdatedAt())
                .submissionCount(request.getSubmissionCount())
                .requestedFields(parsedFields)
                .portfolioLink(request.getPortfolioLink())
                .experience(request.getExperience())
                .certificates(certificatesDto)
                .build();
    }
}
