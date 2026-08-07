package com.cmcu.itstudy.repository.custom;

import com.cmcu.itstudy.enums.DocumentPreviewArtifactKind;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable snapshot of a successfully claimed
 * {@code DocumentPreviewArtifact} row.
 *
 * <p>This is the value handed back to the worker by
 * {@link DocumentPreviewArtifactClaimRepository#claim(int, LocalDateTime, LocalDateTime)}.
 * All fields are read from the same SQL Server
 * {@code UPDATE ... OUTPUT inserted.*} statement that flipped the row
 * from {@code PENDING|RETRY|stale-PROCESSING} to {@code PROCESSING}, so
 * the snapshot reflects the row state at the exact instant of the
 * transition.</p>
 *
 * <p>This type deliberately does NOT expose:</p>
 * <ul>
 *   <li>the full {@link com.cmcu.itstudy.entity.DocumentPreviewArtifact}
 *       managed entity (the worker only needs the columns below);</li>
 *   <li>{@code Document} / {@code DocumentFile} JPA graphs
 *       (re-fetched by the worker when needed);</li>
 *   <li>original bytes, PDF bytes, signed URLs, Supabase identifiers or
 *       storage credentials.</li>
 * </ul>
 *
 * <p>The {@code claimedAt} value is the {@code claimed_at} timestamp
 * written by the same UPDATE that produced the OUTPUT row.</p>
 */
public final class DocumentPreviewArtifactClaim {

    private final UUID artifactId;
    private final UUID documentFileId;
    private final DocumentPreviewArtifactKind artifactKind;
    private final String sourceChecksumSha256;
    private final int variantVersion;
    private final int attemptCount;
    private final int maxAttempts;
    private final LocalDateTime claimedAt;

    public DocumentPreviewArtifactClaim(
            UUID artifactId,
            UUID documentFileId,
            DocumentPreviewArtifactKind artifactKind,
            String sourceChecksumSha256,
            int variantVersion,
            int attemptCount,
            int maxAttempts,
            LocalDateTime claimedAt) {
        this.artifactId = Objects.requireNonNull(artifactId, "artifactId");
        this.documentFileId =
                Objects.requireNonNull(documentFileId, "documentFileId");
        this.artifactKind =
                Objects.requireNonNull(artifactKind, "artifactKind");
        // sourceChecksumSha256 may legitimately be null for legacy
        // sources; do NOT requireNonNull it.
        this.sourceChecksumSha256 = sourceChecksumSha256;
        if (variantVersion < 0) {
            throw new IllegalArgumentException(
                    "variantVersion must be >= 0: " + variantVersion);
        }
        this.variantVersion = variantVersion;
        if (attemptCount < 1) {
            throw new IllegalArgumentException(
                    "attemptCount must be >= 1 (the claim already "
                            + "incremented it): " + attemptCount);
        }
        this.attemptCount = attemptCount;
        if (maxAttempts < 1) {
            throw new IllegalArgumentException(
                    "maxAttempts must be >= 1: " + maxAttempts);
        }
        this.maxAttempts = maxAttempts;
        this.claimedAt = Objects.requireNonNull(claimedAt, "claimedAt");
    }

    public UUID artifactId() {
        return artifactId;
    }

    public UUID documentFileId() {
        return documentFileId;
    }

    public DocumentPreviewArtifactKind artifactKind() {
        return artifactKind;
    }

    public String sourceChecksumSha256() {
        return sourceChecksumSha256;
    }

    public int variantVersion() {
        return variantVersion;
    }

    /**
     * The {@code attempt_count} AFTER the claim SQL has incremented it.
     * The worker MUST use this value as the {@code claimedAttemptCount}
     * guard when performing any subsequent
     * {@code markReady / markRetry / markDead} update.
     */
    public int attemptCount() {
        return attemptCount;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public LocalDateTime claimedAt() {
        return claimedAt;
    }

    /**
     * @return {@code true} when this claim still has at least one
     *         attempt left under its budget.
     */
    public boolean hasAttemptsRemaining() {
        return attemptCount < maxAttempts;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DocumentPreviewArtifactClaim that)) return false;
        return variantVersion == that.variantVersion
                && attemptCount == that.attemptCount
                && maxAttempts == that.maxAttempts
                && artifactId.equals(that.artifactId)
                && documentFileId.equals(that.documentFileId)
                && artifactKind == that.artifactKind
                && Objects.equals(
                        sourceChecksumSha256, that.sourceChecksumSha256)
                && claimedAt.equals(that.claimedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                artifactId,
                documentFileId,
                artifactKind,
                sourceChecksumSha256,
                variantVersion,
                attemptCount,
                maxAttempts,
                claimedAt);
    }

    @Override
    public String toString() {
        return "DocumentPreviewArtifactClaim{artifactId=" + artifactId
                + ", documentFileId=" + documentFileId
                + ", artifactKind=" + artifactKind
                + ", sourceChecksumSha256='"
                + (sourceChecksumSha256 == null
                        ? "<legacy-null>"
                        : sourceChecksumSha256)
                + '\''
                + ", variantVersion=" + variantVersion
                + ", attemptCount=" + attemptCount
                + ", maxAttempts=" + maxAttempts
                + ", claimedAt=" + claimedAt
                + '}';
    }
}
