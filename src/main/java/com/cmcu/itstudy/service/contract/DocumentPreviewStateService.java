package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.dto.document.DocumentPreviewArtifactStatusDto;

import java.util.Optional;
import java.util.UUID;

/**
 * Read-only service that resolves the safe preview-state descriptor for
 * a document, used by the secure preview endpoint when async Office
 * preview is enabled.
 *
 * <p>The returned status is the status of the latest canonical FULL
 * preview artifact. If no FULL artifact exists yet, the descriptor
 * surfaces {@link DocumentPreviewArtifactStatusDto#PENDING}.</p>
 *
 * <p>This service is purely additive: it never returns Office bytes,
 * storage paths, signed URLs, or service credentials.</p>
 */
public interface DocumentPreviewStateService {

    /**
     * Snapshot of the current FULL preview artifact state for the given
     * document. The descriptor is intended for direct serialization
     * inside the preview HTTP response and MUST only contain safe fields.
     *
     * @return the safe state descriptor; never {@code null}
     */
    PreviewState resolve(UUID documentId);

    /**
     * Immutable snapshot. {@code officeDocument} is {@code false} when
     * the document is not a DOC/DOCX Office file and the preview
     * endpoint should fall through to its non-Office code path.
     */
    record PreviewState(
            boolean officeDocument,
            DocumentPreviewArtifactStatusDto fullStatus,
            String safeMessage,
            boolean retryable) {

        /**
         * Returns a non-office sentinel — callers MUST short-circuit
         * the state descriptor branch when the document is not Office.
         */
        public static PreviewState nonOffice() {
            return new PreviewState(false, null, null, false);
        }

        /**
         * Returns the safe waiting descriptor for an Office document
         * whose FULL artifact is not yet present.
         */
        public static PreviewState waiting(DocumentPreviewArtifactStatusDto status,
                                           String safeMessage, boolean retryable) {
            return new PreviewState(true, status, safeMessage, retryable);
        }
    }
}
