package com.cmcu.itstudy.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
@Entity
@Table(
        name = "tbl_community_post_reports",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_one_report_per_user_post", columnNames = {"post_id", "reporter_user_id"})
        }
)
public class CommunityPostReport {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", columnDefinition = "uniqueidentifier")
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    @ToString.Exclude
    @JsonIgnore
    private CommunityPost post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_user_id", nullable = false)
    @ToString.Exclude
    @JsonIgnore
    private User reporter;

    @Column(name = "reason_code", nullable = false, length = 64)
    private String reasonCode;

    @Column(name = "detail", columnDefinition = "nvarchar(max)")
    private String detail;

    @Column(name = "status", nullable = false, length = 32)
    private String status; // PENDING, RESOLVED, DISMISSED

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by_user_id")
    @ToString.Exclude
    @JsonIgnore
    private User resolvedBy;

    @Column(name = "escalation_reason", columnDefinition = "nvarchar(max)")
    private String escalationReason;

    @Column(name = "escalated_at")
    private LocalDateTime escalatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "escalated_by_user_id")
    @ToString.Exclude
    @JsonIgnore
    private User escalatedBy;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = "PENDING";
        }
    }
}
