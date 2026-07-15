package com.cmcu.itstudy.entity;

import com.cmcu.itstudy.enums.WithdrawalStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(
        name = "tbl_withdrawal_requests",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_withdrawal_request_code",
                        columnNames = { "request_code" }
                ),
                @UniqueConstraint(
                        name = "uk_withdrawal_seller_client_request",
                        columnNames = {
                                "seller_id",
                                "client_request_id"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_withdrawal_seller_status",
                        columnList = "seller_id,status"
                ),
                @Index(
                        name = "idx_withdrawal_status_created",
                        columnList = "status,created_at"
                )
        }
)
@Check(constraints = "amount > 0")
public class WithdrawalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", columnDefinition = "uniqueidentifier")
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(
            name = "request_code",
            nullable = false,
            length = 32
    )
    private String requestCode;

    @Column(
            name = "client_request_id",
            nullable = false,
            columnDefinition = "uniqueidentifier"
    )
    private UUID clientRequestId;

    @Column(
            name = "seller_id",
            nullable = false,
            columnDefinition = "uniqueidentifier"
    )
    private UUID sellerId;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WithdrawalStatus status;

    @Column(name = "bank_code", nullable = false, length = 32)
    private String bankCode;

    @Column(name = "bank_name", nullable = false, columnDefinition = "nvarchar(255)")
    private String bankName;

    @Column(name = "bank_account_number", nullable = false, length = 64)
    private String bankAccountNumber;

    @Column(
            name = "bank_account_holder_name",
            nullable = false,
            columnDefinition = "nvarchar(255)"
    )
    private String bankAccountHolderName;

    @Column(name = "seller_note", columnDefinition = "nvarchar(1000)")
    private String sellerNote;

    @Column(name = "admin_note", columnDefinition = "nvarchar(1000)")
    private String adminNote;

    @Column(name = "approved_by_admin_id", columnDefinition = "uniqueidentifier")
    private UUID approvedByAdminId;

    @Column(name = "paid_by_admin_id", columnDefinition = "uniqueidentifier")
    private UUID paidByAdminId;

    @Column(name = "rejected_by_admin_id", columnDefinition = "uniqueidentifier")
    private UUID rejectedByAdminId;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();

        if (createdAt == null) {
            createdAt = now;
        }

        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}