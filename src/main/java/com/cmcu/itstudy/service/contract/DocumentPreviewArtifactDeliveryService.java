package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.dto.document.PreviewMode;
import com.cmcu.itstudy.entity.DocumentPreviewArtifact;
import com.cmcu.itstudy.enums.DocumentPreviewArtifactStatus;
import com.cmcu.itstudy.handle.PreviewArtifactDeliveryException;
import com.cmcu.itstudy.handle.PreviewArtifactStateRaceException;
import com.cmcu.itstudy.handle.SignedUploadTargetFailedException;
import com.cmcu.itstudy.handle.StorageObjectNotFoundException;

import java.util.UUID;

/**
 * Phase&nbsp;O4B artifact delivery contract.
 *
 * <p>The READY Office preview endpoint MUST NOT invoke the original-file
 * preview builder; instead it MUST download the selected READY PDF
 * artifact directly from the artifact's own
 * {@code storageBucket}/{@code storagePath} and stream those bytes back to
 * the client. This contract is the single boundary that performs that
 * delivery.</p>
 *
 * <h2>Why a dedicated service</h2>
 * <p>The legacy {@code PaidDocumentPreviewService.buildPreview(...)} path
 * downloads from {@code DocumentFile.bucket}/{@code DocumentFile.path}
 * (the original DOC/DOCX bytes). Under async Office preview, the canonical
 * preview artifact is a separate PDF that lives at
 * {@code DocumentPreviewArtifact.storageBucket}/{@code DocumentPreviewArtifact.storagePath}.
 * Mixing the two pipelines would (a) leak the original Office bytes for
 * READY Office artifacts, (b) silently fall through to LibreOffice /
 * POI conversion during a preview request, and (c) allow a single artifact
 * to be substituted with the wrong kind (FULL substituted for LIMITED).
 * This contract prevents all three.</p>
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>The caller MUST supply the authoritative
 *       {@link com.cmcu.itstudy.dto.document.PreviewMode} (FULL or LIMITED)
 *       produced by the metadata-only
 *       {@link com.cmcu.itstudy.service.contract.PreviewAccessDecisionService}.
 *       {@code LOCKED} is rejected — a locked user cannot reach this
 *       contract.</li>
 *   <li>The selected artifact MUST be in status
 *       {@link DocumentPreviewArtifactStatus#READY}.</li>
 *   <li>The selected artifact's kind MUST equal the authorized mode.</li>
 *   <li>The selected artifact's bucket / path MUST be non-blank.</li>
 *   <li>Storage IO MUST NOT run inside a database transaction.</li>
 *   <li>The bytes returned are the PDF artifact bytes. The contract never
 *       returns DOC/DOCX/DOC_HTML.</li>
 * </ul>
 */
public interface DocumentPreviewArtifactDeliveryService {

    /**
     * Deliver the READY PDF artifact for the given document and the
     * authorized preview mode.
     *
     * <p>Failure semantics:</p>
     * <ul>
     *   <li>{@link PreviewArtifactStateRaceException} &mdash; the
     *       artifact row's status was changed by the worker between
     *       the controller's READY check and the read-time
     *       re-validation. The controller re-queries the preview
     *       state service and surfaces the current waiting/dead
     *       state.</li>
     *   <li>{@link PreviewArtifactDeliveryException} &mdash;
     *       terminal delivery error: the artifact row is well-formed
     *       but blank bucket / wrong kind / non-PDF bytes / zero
     *       pages. The controller translates this into safe terminal
     *       JSON that stops polling.</li>
     *   <li>{@link StorageObjectNotFoundException} &mdash; the
     *       artifact's bucket/path is well-formed but the storage
     *       object is missing. Terminal delivery error.</li>
     *   <li>{@link SignedUploadTargetFailedException} &mdash;
     *       storage transport failure. The controller treats this as
     *       a transient retryable state (or terminal, see
     *       controller).</li>
     *   <li>Other {@link RuntimeException} &mdash; the controller
     *       does NOT catch this. The global exception handler
     *       produces a safe 500 response. We never disguise an
     *       unexpected failure as a PENDING waiting state.</li>
     * </ul>
     *
     * @param documentId        document id (resolved server-side)
     * @param authorizedMode    the access decision (FULL or LIMITED only)
     * @return the verified READY artifact and its PDF bytes
     * @throws IllegalArgumentException         when {@code authorizedMode}
     *                                          is {@code LOCKED}
     * @throws PreviewArtifactStateRaceException when the artifact's
     *                                          status is no longer READY
     *                                          at read time
     * @throws PreviewArtifactDeliveryException terminal delivery error
     * @throws StorageObjectNotFoundException   storage object missing
     * @throws SignedUploadTargetFailedException storage transport failure
     */
    DeliveredArtifact deliverReadyArtifact(UUID documentId, PreviewMode authorizedMode);

    /**
     * Bundle returned by the delivery service. Carries both the
     * artifact row (for diagnostics and headers) and the verified PDF
     * bytes (for streaming). Bytes are non-null and non-empty.
     */
    record DeliveredArtifact(
            DocumentPreviewArtifact artifact,
            byte[] pdfBytes,
            int pageCount) {
    }
}