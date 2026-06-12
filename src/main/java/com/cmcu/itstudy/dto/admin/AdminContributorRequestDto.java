package com.cmcu.itstudy.dto.admin;

import com.cmcu.itstudy.enums.ContributorRequestStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminContributorRequestDto {
    private UUID id;
    /** Id người dùng (để admin gán role qua /api/admin/users/...). */
    private UUID userId;
    private String name; // Tên người dùng gửi yêu cầu
    private String email; // Email người dùng gửi yêu cầu
    private String portfolioLink;
    private String experience;
    private LocalDateTime createdAt;
    private ContributorRequestStatus status; // Trạng thái: PENDING, APPROVED, REJECTED
    private List<AdminContributorCertificateDto> certificates;
    private String avatarUrl; // URL avatar của người dùng
    private String rejectionReason; // Lý do từ chối hoặc yêu cầu bổ sung thông tin
    /** Số lần admin đã yêu cầu bổ sung (tối đa 3). */
    private int supplementCount;
    /** Map tên trường -> lý do cần bổ sung. */
    private Map<String, String> requestedFields;
}
