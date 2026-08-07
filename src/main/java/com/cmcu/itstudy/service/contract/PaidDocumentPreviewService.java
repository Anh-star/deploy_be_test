package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.dto.document.PreviewLockedReason;
import com.cmcu.itstudy.dto.document.PreviewMode;
import com.cmcu.itstudy.entity.User;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

/**
 * Result of a paid preview generation.
 *
 * <p>The contract guarantees:
 * <ul>
 *   <li>Bucket / path / signed URL / token are NEVER present on this
 *       type — the public surface intentionally carries no field that
 *       could leak the private storage location.</li>
 *   <li>The {@link #bytes()} array is non-null for {@link #mode} ==
 *       {@code FULL} or {@code LIMITED}; it is null for {@code LOCKED}.</li>
 *   <li>{@link #lockedReason()} is non-null only for {@code LOCKED}.</li>
 * </ul>
 */
public interface PaidDocumentPreviewService {

    PreviewResult buildPreview(DocumentRequest request);

    /**
     * Inputs the preview service needs. The {@code bucket} and
     * {@code path} come exclusively from a server-resolved
     * {@code DocumentFile} row; they are NEVER read from a client
     * payload.
     */
    record DocumentRequest(
            java.util.UUID documentId,
            String bucket,
            String path,
            String mimeType,
            com.cmcu.itstudy.enums.DocumentStatus status,
            Boolean isPaid,
            java.util.UUID ownerId,
            User currentUser,
            Collection<? extends GrantedAuthority> authorities,
            boolean hasPurchaserAccess) {
    }

    record PreviewResult(
            PreviewMode mode,
            byte[] bytes,
            Integer totalPages,
            Integer visiblePages,
            PreviewLockedReason lockedReason,
            String message,
            RendererKind renderer) {

        public static PreviewResult full(byte[] bytes, int totalPages, RendererKind renderer) {
            return new PreviewResult(PreviewMode.FULL, bytes, totalPages, totalPages,
                    null, null, renderer);
        }

        public static PreviewResult limited(byte[] bytes, int totalPages, int visiblePages, RendererKind renderer) {
            return new PreviewResult(PreviewMode.LIMITED, bytes, totalPages,
                    visiblePages, null, null, renderer);
        }

        public static PreviewResult locked(PreviewLockedReason reason, String message) {
            return new PreviewResult(PreviewMode.LOCKED, null, null, null,
                    reason, message, RendererKind.PDF);
        }

        public static PreviewResult denied() {
            return new PreviewResult(PreviewMode.LOCKED, null, null, null,
                    PreviewLockedReason.PREVIEW_UNAVAILABLE,
                    "Bạn không có quyền xem tài liệu này",
                    RendererKind.PDF);
        }
    }

    enum RendererKind { PDF, DOCX, DOC, DOC_HTML }
}