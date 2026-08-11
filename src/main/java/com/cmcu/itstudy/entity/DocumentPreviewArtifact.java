package com.cmcu.itstudy.entity;

import com.cmcu.itstudy.enums.DocumentPreviewArtifactKind;
import com.cmcu.itstudy.enums.DocumentPreviewArtifactStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Persistence model for a generated Office preview artifact (full or
 * limited derivative) attached to a {@link DocumentFile}.
 *
 * <p>Phase&nbsp;O2 introduces this entity to record the worker claim
 * lifecycle. The entity deliberately stores NO remote-storage bytes, NO
 * signed URLs and NO Supabase identifiers &mdash; it only carries the
 * minimum amount of metadata the future worker (Phase&nbsp;O3) needs to
 * resume processing after a crash and to reconcile the artifact with the
 * authoritative upload/cleanup queues.</p>
 *
 * <h2>Schema</h2>
 * <p>Mapped to {@code dbo.tbl_document_preview_artifacts}.</p>
 *
 * <h2>Business key</h2>
 * <p>For a source that already has a {@link DocumentFile#getChecksumSha256()
 * checksum}, the business key is:</p>
 * <pre>(document_file_id, artifact_kind, source_checksum_sha256, variant_version)</pre>
 * <p>For legacy sources whose checksum is {@code NULL}, the business key
 * is:</p>
 * <pre>(document_file_id, artifact_kind, variant_version)</pre>
 * <p>The two filtered unique indexes that materialize those keys are
 * NOT expressed through {@code @Table(uniqueConstraints=...)} and are
 * installed out-of-band by operator-run SQL (see the O2 report).</p>
 *
 * <h2>Lifecycle</h2>
 * <ul>
 *   <li>Created in status {@link DocumentPreviewArtifactStatus#PENDING}
 *       by Phase&nbsp;O3 upload flow.</li>
 *   <li>Claimed by the worker via
 *       {@code DocumentPreviewArtifactClaimRepository.claim(...)} which
 *       atomically transitions {@code PENDING|RETRY|stale-PROCESSING}
 *       rows to {@code PROCESSING}.</li>
 *   <li>Marked {@code READY} when the upload to Supabase completes
 *       successfully.</li>
 *   <li>Marked {@code RETRY} on retryable failure; the worker may
 *       re-claim it after {@code next_attempt_at}.</li>
 *   <li>Marked {@code DEAD} on terminal failure or when
 *       {@code attempt_count >= max_attempts}.</li>
 * </ul>
 *
 * <h2>Why a direct UUID FK column instead of a {@code @ManyToOne}?</h2>
 * <p>The atomic claim path returns an immutable
 * {@code DocumentPreviewArtifactClaim} snapshot built from a single
 * SQL Server UPDATE ... OUTPUT statement. That statement does not
 * hydrate JPA managed entities. The reference to {@link DocumentFile}
 * is therefore persisted as a UUID {@code document_file_id} column and
 * re-resolved in a subsequent service call only when needed.</p>
 *
 * <h2>Cleanup task reference</h2>
 * <p>{@code cleanup_task_id} references {@code
 * dbo.tbl_storage_cleanup_tasks.id} (a {@code bigint}). Phase&nbsp;O2
 * only SETS this column to {@code null} &mdash; it never inserts cleanup
 * tasks itself. A row with {@code cleanup_task_id != null} is not
 * claimable.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
@Entity
@Table(name = "tbl_document_preview_artifacts", schema = "dbo")
public class DocumentPreviewArtifact {

    /** Maximum length for {@code source_checksum_sha256} (SHA-256 hex). */
    public static final int CHECKSUM_MAX_LENGTH = 64;

    /** Maximum length for {@code storage_bucket}. */
    public static final int STORAGE_BUCKET_MAX_LENGTH = 100;

    /** Maximum length for {@code storage_path}. */
    public static final int STORAGE_PATH_MAX_LENGTH = 1000;

    /** Maximum length for {@code last_error}. */
    public static final int LAST_ERROR_MAX_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", columnDefinition = "uniqueidentifier")
    @EqualsAndHashCode.Include
    private UUID id;

    /**
     * FK to {@code dbo.tbl_document_files.id}. Persisted as a UUID
     * column rather than a JPA association so the atomic claim path
     * does not need to hydrate {@link DocumentFile}.
     */
    @Column(name = "document_file_id", nullable = false,
            columnDefinition = "uniqueidentifier")
    private UUID documentFileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "artifact_kind", nullable = false, length = 20)
    private DocumentPreviewArtifactKind artifactKind;

    /**
     * Source-document checksum at the moment the artifact was created.
     * Nullable because legacy {@link DocumentFile} rows can have a
     * {@code NULL} checksum; that case uses the legacy business key
     * (no checksum column).
     */
    @Column(name = "source_checksum_sha256", length = CHECKSUM_MAX_LENGTH)
    private String sourceChecksumSha256;

    @Column(name = "variant_version", nullable = false)
    private Integer variantVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DocumentPreviewArtifactStatus status;

    @Column(name = "storage_bucket", length = STORAGE_BUCKET_MAX_LENGTH)
    private String storageBucket;

    @Column(name = "storage_path", length = STORAGE_PATH_MAX_LENGTH)
    private String storagePath;

    @Column(name = "total_pages")
    private Integer totalPages;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private Integer maxAttempts;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @Column(name = "last_error", length = LAST_ERROR_MAX_LENGTH)
    private String lastError;

    /**
     * FK to {@code dbo.tbl_storage_cleanup_tasks.id}. Persisted as a
     * {@code bigint} column to match the cleanup task table's PK type.
     * Nullable; when non-null the row is not claimable in Phase&nbsp;O2.
     */
    @Column(name = "cleanup_task_id")
    private Long cleanupTaskId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @JsonIgnore
    public boolean isChecksummed() {
        return sourceChecksumSha256 != null && !sourceChecksumSha256.isBlank();
    }

    /**
     * UTC clock used by {@link #prePersist()} when the factory did not
     * stamp timestamps explicitly. {@link Clock#systemUTC()} returns a
     * clock whose zone is fixed to UTC regardless of the JVM's default
     * time-zone, so the {@code LocalDateTime} resolved here is the same
     * instant regardless of whether the host is in
     * {@code Asia/Ho_Chi_Minh} or {@code America/New_York}.
     *
     * <p>This constant is package-private so unit tests can verify that
     * the fallback clock is UTC.</p>
     */
    static final Clock PRE_PERSIST_FALLBACK_CLOCK = Clock.systemUTC();

    @PrePersist
    void prePersist() {
        // The factory is the canonical producer of these timestamps
        // and stamps createdAt/updatedAt/nextAttemptAt/status/
        // attemptCount/maxAttempts from the application Clock (UTC).
        // When the factory is bypassed — for example by an ad-hoc
        // repository.save(...) — this callback fills the gaps with
        // UTC-based defaults so the row is still immediately claimable
        // by the worker (next_attempt_at <= worker.now) and the rest
        // of the preview pipeline, which also uses UTC, sees consistent
        // values. The fallback clock is NEVER the JVM default zone.
        LocalDateTime now = LocalDateTime.now(PRE_PERSIST_FALLBACK_CLOCK);
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.status == null) {
            this.status = DocumentPreviewArtifactStatus.PENDING;
        }
        if (this.attemptCount == null) {
            this.attemptCount = 0;
        }
        if (this.maxAttempts == null) {
            this.maxAttempts = 5;
        }
        if (this.nextAttemptAt == null) {
            this.nextAttemptAt = now;
        }
    }

    @PreUpdate
    void preUpdate() {
        // Same UTC fallback rationale as @PrePersist: the worker and
        // the state service supply explicit values, but a caller that
        // mutates the entity without one of those helpers still gets
        // an updatedAt that agrees with the UTC-based clock used by
        // the rest of the preview pipeline.
        this.updatedAt = LocalDateTime.now(PRE_PERSIST_FALLBACK_CLOCK);
    }
}
