package com.cmcu.itstudy.entity;

import com.cmcu.itstudy.enums.ContributorRequestStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tbl_contributor_requests")
public class ContributorRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", columnDefinition = "uniqueidentifier")
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "portfolio_link")
    private String portfolioLink;

    @Column(name = "experience", nullable = false, columnDefinition = "nvarchar(1000)")
    private String experience;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private ContributorRequestStatus status;

    @Column(name = "certificate_name", columnDefinition = "nvarchar(255)")
    private String certificateName;

    @Column(name = "certificate_url")
    private String certificateUrl;

    @Column(name = "rejection_reason", columnDefinition = "nvarchar(1000)")
    private String rejectionReason;

    /** Số lần đã gửi hồ sơ (tối đa 2). */
    @Column(name = "submission_count", nullable = false)
    @Builder.Default
    private int submissionCount = 1;

    /** Số lần admin đã yêu cầu bổ sung thông tin (tối đa 3). */
    @Column(name = "supplement_count", nullable = false)
    @Builder.Default
    private int supplementCount = 0;

    /** Danh sách các trường cần bổ sung, phân cách bằng dấu phẩy (vd: "fullName,email,portfolioLink"). */
    @Column(name = "requested_fields", columnDefinition = "nvarchar(1000)")
    private String requestedFields;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<ContributorCertificate> certificates = new ArrayList<>();
}
