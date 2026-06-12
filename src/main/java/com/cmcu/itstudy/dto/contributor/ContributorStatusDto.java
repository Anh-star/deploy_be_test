package com.cmcu.itstudy.dto.contributor;

import com.cmcu.itstudy.enums.ContributorRequestStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContributorStatusDto {
    private ContributorRequestStatus status;
    private String rejectionReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private int submissionCount;
    /** Map tên trường -> lý do cần bổ sung (khi status = NEED_INFO). */
    private Map<String, String> requestedFields;
    private String portfolioLink;
    private String experience;
    private List<CertificateDto> certificates;
}
