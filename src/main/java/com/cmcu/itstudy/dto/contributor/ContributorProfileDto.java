package com.cmcu.itstudy.dto.contributor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Full contributor profile DTO returned by GET /api/contributor/profile.
 * Aggregates user data with the latest contributor request data.
 *
 * <p>Ngày tham gia (contributorApprovedAt) phải là thời điểm user thực sự
 * được phê duyệt — KHÔNG dùng submittedAt hay userCreatedAt.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContributorProfileDto {

    /* ---- User-level fields ---- */
    private String fullName;
    private String email;
    private String phone;
    private String bio;
    private String avatarUrl;

    /* ---- Contributor registration fields ---- */
    private String experience;
    private String portfolioLink;
    private List<CertificateDto> certificates;

    /* ---- Timeline ---- */
    /** Ngày user trở thành Contributor (updatedAt của request APPROVED). Null nếu chưa được duyệt. */
    private LocalDateTime contributorApprovedAt;

    /** Ngày đơn gần nhất được gửi (createdAt của request gần nhất). */
    private LocalDateTime latestRequestSubmittedAt;

    /** Ngày user đăng ký tài khoản trên hệ thống. */
    private LocalDateTime userCreatedAt;

    /** Trạng thái của đơn contributor gần nhất. */
    private String requestStatus;
}

