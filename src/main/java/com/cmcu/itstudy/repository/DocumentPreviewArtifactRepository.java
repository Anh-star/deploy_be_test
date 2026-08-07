package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.DocumentPreviewArtifact;
import com.cmcu.itstudy.enums.DocumentPreviewArtifactKind;
import com.cmcu.itstudy.enums.DocumentPreviewArtifactStatus;
import com.cmcu.itstudy.repository.custom.DocumentPreviewArtifactClaimRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link DocumentPreviewArtifact}.
 *
 * <p>The custom fragment
 * {@link DocumentPreviewArtifactClaimRepository} supplies the atomic
 * claim SQL; the methods declared here handle ordinary read-only
 * lookups required by the upload / moderator flow (Phase&nbsp;O3+) and
 * never expose unsafe mutable state.</p>
 *
 * <h2>Business-key lookups</h2>
 * <p>Two distinct methods cover the two business keys mandated by the
 * Phase&nbsp;O2 contract:</p>
 * <ul>
 *   <li>{@link #findChecksummed(UUID, DocumentPreviewArtifactKind, String, int)}
 *       &mdash; for sources that already have a non-null checksum.</li>
 *   <li>{@link #findLegacyNullChecksum(UUID, DocumentPreviewArtifactKind, int)}
 *       &mdash; for sources whose {@code checksum_sha256} is {@code NULL}
 *       in {@code dbo.tbl_document_files}.</li>
 * </ul>
 * <p>These two methods MUST NOT be merged into a single
 * {@code findFirst...By...IgnoreCase} or similar ambiguous query
 * &mdash; the legacy null-checksum branch must be tested explicitly to
 * avoid silent collisions with the new/checksummed branch.</p>
 */
@Repository
public interface DocumentPreviewArtifactRepository
        extends JpaRepository<DocumentPreviewArtifact, UUID>,
        DocumentPreviewArtifactClaimRepository {

    Optional<DocumentPreviewArtifact> findById(UUID id);

    /**
     * Looks up a checksummed artifact (the source
     * {@link com.cmcu.itstudy.entity.DocumentFile} has a non-null
     * checksum).
     */
    Optional<DocumentPreviewArtifact> findByDocumentFileIdAndArtifactKindAndSourceChecksumSha256AndVariantVersion(
            UUID documentFileId,
            DocumentPreviewArtifactKind artifactKind,
            String sourceChecksumSha256,
            int variantVersion);

    /**
     * Looks up a legacy null-checksum artifact (the source
     * {@link com.cmcu.itstudy.entity.DocumentFile} has a
     * {@code NULL} checksum).
     */
    Optional<DocumentPreviewArtifact> findFirstByDocumentFileIdAndArtifactKindAndSourceChecksumSha256IsNullAndVariantVersionOrderByCreatedAtDesc(
            UUID documentFileId,
            DocumentPreviewArtifactKind artifactKind,
            int variantVersion);

    /**
     * Returns the current READY artifact for the given source. The
     * worker uses this to short-circuit re-processing when a fresh
     * preview is already available.
     */
    Optional<DocumentPreviewArtifact> findFirstByDocumentFileIdAndArtifactKindAndSourceChecksumSha256AndStatusAndVariantVersion(
            UUID documentFileId,
            DocumentPreviewArtifactKind artifactKind,
            String sourceChecksumSha256,
            DocumentPreviewArtifactStatus status,
            int variantVersion);

    /**
     * Returns all artifacts for the given source (any kind/status).
     * Used for diagnostic listings.
     */
    List<DocumentPreviewArtifact> findByDocumentFileId(UUID documentFileId);

    /**
     * Returns {@code true} when a READY FULL artifact exists for the
     * given source / checksum / variant.
     */
    boolean existsByDocumentFileIdAndArtifactKindAndSourceChecksumSha256AndStatusAndVariantVersion(
            UUID documentFileId,
            DocumentPreviewArtifactKind artifactKind,
            String sourceChecksumSha256,
            DocumentPreviewArtifactStatus status,
            int variantVersion);

    /**
     * Returns the most recent artifact for the given source and kind,
     * ordered deterministically by {@code createdAt DESC, id DESC}.
     * The {@code id} tie-breaker is required because two FULL artifacts
     * can legitimately share the same {@code createdAt} when the
     * worker batched two uploads within the same wall-clock second.
     *
     * <p>The artifact with the latest {@code createdAt} is the newest
     * version and represents the authoritative current state.</p>
     */
    Optional<DocumentPreviewArtifact> findFirstByDocumentFileIdAndArtifactKindOrderByCreatedAtDescIdDesc(
            UUID documentFileId,
            DocumentPreviewArtifactKind artifactKind);
}
