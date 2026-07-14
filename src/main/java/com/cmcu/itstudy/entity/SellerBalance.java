package com.cmcu.itstudy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
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
@Table(name = "tbl_seller_balances")
@Check(constraints = "pending_balance >= 0 AND available_balance >= 0 AND locked_balance >= 0 AND total_earned >= 0 AND total_withdrawn >= 0")
public class SellerBalance {

    @Id
    @Column(
        name = "seller_id",
        nullable = false,
        columnDefinition = "uniqueidentifier"
    )
    @EqualsAndHashCode.Include
    private UUID sellerId;

    @Builder.Default
    @Column(name = "pending_balance", nullable = false)
    private Long pendingBalance = 0L;

    @Builder.Default
    @Column(name = "available_balance", nullable = false)
    private Long availableBalance = 0L;

    @Builder.Default
    @Column(name = "locked_balance", nullable = false)
    private Long lockedBalance = 0L;

    @Builder.Default
    @Column(name = "total_earned", nullable = false)
    private Long totalEarned = 0L;

    @Builder.Default
    @Column(name = "total_withdrawn", nullable = false)
    private Long totalWithdrawn = 0L;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (pendingBalance == null) {
            pendingBalance = 0L;
        }
        if (availableBalance == null) {
            availableBalance = 0L;
        }
        if (lockedBalance == null) {
            lockedBalance = 0L;
        }
        if (totalEarned == null) {
            totalEarned = 0L;
        }
        if (totalWithdrawn == null) {
            totalWithdrawn = 0L;
        }
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
