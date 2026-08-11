package com.cmcu.itstudy.entity;

import com.cmcu.itstudy.enums.PendingUploadStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.domain.Persistable;

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
        name = "tbl_pending_storage_uploads",
        schema = "dbo",
        uniqueConstraints = @UniqueConstraint(
                name = "UQ_tbl_pending_storage_uploads_bucket_path",
                columnNames = {"storage_bucket", "storage_path"}
        )
)
public class PendingStorageUpload implements Persistable<UUID> {

    /**
     * Application-generated UUID. Created via {@link UUID#randomUUID()}
     * before {@link com.cmcu.itstudy.repository.PendingStorageUploadRepository#save(Object)}
     * so the storage path can reference the same identifier. Hibernate does
     * NOT generate this value.
     */
    @Id
    @Column(
            name = "upload_id",
            nullable = false,
            columnDefinition = "uniqueidentifier"
    )
    @EqualsAndHashCode.Include
    private UUID uploadId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    @JsonIgnore
    private User user;

    @Column(name = "storage_bucket", nullable = false, length = 100)
    private String storageBucket;

    @Column(name = "storage_path", nullable = false, length = 1000)
    private String storagePath;

    @Column(name = "expected_file_name", nullable = false, length = 500)
    private String expectedFileName;

    @Column(name = "expected_mime_type", nullable = false, length = 255)
    private String expectedMimeType;

    @Column(name = "expected_size_bytes", nullable = false)
    private Long expectedSizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PendingUploadStatus status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bound_document_id")
    @ToString.Exclude
    @JsonIgnore
    private Document boundDocument;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Lifecycle callback for the {@code created_at}/{@code updated_at}
     * columns. The orchestrator normally supplies both values via
     * {@link com.cmcu.itstudy.service.contract.PendingUploadRegistrationService#register}
     * so timestamps stay consistent with the application clock. This
     * callback only fills the fields when they are still {@code null},
     * so it does NOT overwrite caller-supplied timestamps.
     */
    @PrePersist
    void prePersist() {
        LocalDateTime lifecycleNow = LocalDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = lifecycleNow;
        }
        if (this.updatedAt == null) {
            this.updatedAt = this.createdAt;
        }
        if (this.status == null) {
            this.status = PendingUploadStatus.PENDING;
        }
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Persistence marker for Spring Data JPA. We want INSERT (not MERGE) when
     * the caller has already populated {@link #uploadId}. The marker field
     * {@code persisted} starts false and is flipped to true the first time
     * Spring Data persists the entity.
     */
    @Transient
    @Builder.Default
    private boolean persisted = false;

    @Override
    public UUID getId() {
        return uploadId;
    }

    @Override
    public boolean isNew() {
        return !persisted;
    }

    @jakarta.persistence.PostPersist
    void markPersisted() {
        this.persisted = true;
    }

    @jakarta.persistence.PostLoad
    void markLoaded() {
        this.persisted = true;
    }
}