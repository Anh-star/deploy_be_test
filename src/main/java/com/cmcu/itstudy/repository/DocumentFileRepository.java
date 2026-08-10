package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.DocumentFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface DocumentFileRepository extends JpaRepository<DocumentFile, UUID> {

    Optional<DocumentFile> findFirstByDocument_IdAndPrimaryTrue(UUID documentId);

    default Optional<DocumentFile> findByDocumentIdAndPrimaryTrue(UUID documentId) {
        return findFirstByDocument_IdAndPrimaryTrue(documentId);
    }

    /**
     * Phase 2C E2E wiring fix — returns the parent {@code Document.id}
     * for a given {@code DocumentFile.id} as a plain UUID projection.
     *
     * <p>This avoids loading and materialising the full {@code Document}
     * entity (and avoids touching the {@code DocumentFile.document}
     * {@code LAZY} association from a non-transactional context, which
     * would throw {@code LazyInitializationException}).</p>
     *
     * <p>Used by {@code DocumentPreviewArtifactProcessor} to obtain the
     * {@code documentId} for the source-ready bridge
     * ({@code QuizGenerationService.queueWhenSourceReady}) without
     * triggering lazy initialization.</p>
     *
     * @param documentFileId the {@code DocumentFile.id} whose owning
     *                       {@code Document.id} is requested
     * @return {@code Optional.of(document.id)} when the {@code DocumentFile}
     *         exists; {@code Optional.empty()} when no such
     *         {@code DocumentFile} exists
     */
    @Query("SELECT df.document.id FROM DocumentFile df "
            + "WHERE df.id = :documentFileId")
    Optional<UUID> findDocumentIdByDocumentFileId(
            @Param("documentFileId") UUID documentFileId);
}

