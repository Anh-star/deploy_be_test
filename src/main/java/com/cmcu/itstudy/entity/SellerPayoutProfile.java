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

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "tbl_seller_payout_profiles")
public class SellerPayoutProfile {

    @Id
    @Column(
            name = "seller_id",
            nullable = false,
            columnDefinition = "uniqueidentifier"
    )
    @EqualsAndHashCode.Include
    private UUID sellerId;

    @Column(
            name = "bank_code",
            nullable = false,
            length = 32
    )
    private String bankCode;

    @Column(
            name = "bank_name",
            nullable = false,
            columnDefinition = "nvarchar(255)"
    )
    private String bankName;

    @Column(
            name = "bank_account_number",
            nullable = false,
            length = 64
    )
    private String bankAccountNumber;

    @Column(
            name = "bank_account_holder_name",
            nullable = false,
            columnDefinition = "nvarchar(255)"
    )
    private String bankAccountHolderName;

    @Column(
            name = "created_at",
            nullable = false
    )
    private LocalDateTime createdAt;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private LocalDateTime updatedAt;

    @Version
    @Column(
            name = "version",
            nullable = false
    )
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
