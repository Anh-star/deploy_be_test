package com.cmcu.itstudy.entity;

import com.cmcu.itstudy.enums.QuizGenerationStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One row per {@link Document} that the uploader wants an AI quiz for.
 *
 * <p>Phase 2B persistence only. No n8n / signed-URL / scheduler / callback
 * wiring lives here. Later phases read this row, dispatch work, and write
 * the resulting {@link Quiz} back into {@link #quiz}.
 *
 * <p>Hard invariants:
 * <ul>
 *   <li>{@code UNIQUE(document_id)} — in V1 a document has at most one
 *       {@link QuizGeneration} row (one history entry per document, not
 *       "one active generation at a time"). The unique constraint is
 *       enforced by the database; concurrent enqueues fail the
 *       transaction instead of silently inserting a duplicate.</li>
 *   <li>QuizGeneration does NOT persist source paths, source buckets,
 *       or signed URLs. The AI-readable source for a future dispatch
 *       is resolved at dispatch time from the associated
 *       {@link DocumentFile} (and its {@link DocumentPreviewArtifact}
 *       for DOC / DOCX) — NOT read from this row.</li>
 * </ul>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
@Entity
@Table(
        name = "tbl_quiz_generations",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_quiz_generation_document",
                columnNames = "document_id"))
public class QuizGeneration {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", columnDefinition = "uniqueidentifier")
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    @ToString.Exclude
    @JsonIgnore
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_file_id", nullable = false)
    @ToString.Exclude
    @JsonIgnore
    private DocumentFile documentFile;

    @Column(name = "requested_question_count", nullable = false)
    private Integer requestedQuestionCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private QuizGenerationStatus status;

    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private Integer attempts = 0;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "last_error", length = 255)
    private String lastError;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id")
    @ToString.Exclude
    @JsonIgnore
    private Quiz quiz;

    @Column(name = "dispatch_token", columnDefinition = "uniqueidentifier")
    private UUID dispatchToken;

    @Column(name = "dispatch_token_issued_at")
    private LocalDateTime dispatchTokenIssuedAt;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "processing_at")
    private LocalDateTime processingAt;

    @Column(name = "ready_at")
    private LocalDateTime readyAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

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
        if (this.attempts == null) {
            this.attempts = 0;
        }
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}