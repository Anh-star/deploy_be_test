package com.cmcu.itstudy.entity;

import com.cmcu.itstudy.enums.SellerEarningStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
    name = "tbl_seller_earnings",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_seller_earnings_payment",
            columnNames = { "payment_id" }
        )
    }
)
public class SellerEarning {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", columnDefinition = "uniqueidentifier")
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "payment_id", nullable = false, columnDefinition = "uniqueidentifier")
    private UUID paymentId;

    @Column(name = "seller_id", nullable = false, columnDefinition = "uniqueidentifier")
    private UUID sellerId;

    @Column(name = "buyer_id", nullable = false, columnDefinition = "uniqueidentifier")
    private UUID buyerId;

    @Column(name = "document_id", nullable = false, columnDefinition = "uniqueidentifier")
    private UUID documentId;

    @Column(name = "document_title_snapshot", nullable = false, columnDefinition = "nvarchar(255)")
    private String documentTitleSnapshot;

    @Column(name = "gross_amount", nullable = false)
    private Long grossAmount;

    @Column(name = "platform_fee", nullable = false)
    private Long platformFee;

    @Column(name = "seller_net_amount", nullable = false)
    private Long sellerNetAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SellerEarningStatus status;

    @Column(name = "available_at", nullable = false)
    private LocalDateTime availableAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
