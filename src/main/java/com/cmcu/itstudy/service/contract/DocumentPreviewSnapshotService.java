package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.entity.Document;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the immutable preview snapshot for a document.
 *
 * <p>The snapshot is intentionally small: only the fields the preview
 * service needs to compute the response. Resolving this snapshot in a
 * dedicated read-only transaction keeps the preview service free of
 * JPA concerns and ensures the database transaction closes BEFORE the
 * Supabase HTTP call happens.
 *
 * <p>Snapshot fields:
 * <ul>
 *   <li>{@code documentId} — primary key.</li>
 *   <li>{@code status} — current moderation status.</li>
 *   <li>{@code isPaid} — paid-document flag.</li>
 *   <li>{@code ownerId} — used by the access decision service.</li>
 *   <li>{@code mimeType} — used to short-circuit non-PDF paid files.</li>
 *   <li>{@code bucket} / {@code path} — Supabase private bucket and
 *       object path resolved from the primary {@code DocumentFile} row.</li>
 * </ul>
 */
public interface DocumentPreviewSnapshotService {

    Optional<DocumentPreviewSnapshot> resolve(UUID documentId);

    /**
     * Immutable record carrying everything the preview service needs to
     * build a response. Bucket / path are never null for documents that
     * have a primary file; absent values result in a LOCKED response
     * with reason {@code PREVIEW_UNAVAILABLE}.
     */
    record DocumentPreviewSnapshot(
            UUID documentId,
            com.cmcu.itstudy.enums.DocumentStatus status,
            Boolean isPaid,
            UUID ownerId,
            String bucket,
            String path,
            String mimeType,
            String fileExtension,
            Boolean deleted,
            Boolean fileCleaned,
            java.time.LocalDateTime retentionExpiresAt) {

        public static DocumentPreviewSnapshot fromDocument(Document document, String bucket,
                                                            String path, String mimeType,
                                                            String fileExtension) {
            return new DocumentPreviewSnapshot(
                    document.getId(),
                    document.getStatus(),
                    document.getIsPaid(),
                    document.getCreatedBy() != null ? document.getCreatedBy().getId() : null,
                    bucket,
                    path,
                    mimeType,
                    fileExtension,
                    document.getDeleted(),
                    document.getFileCleaned(),
                    document.getRetentionExpiresAt());
        }
    }
}