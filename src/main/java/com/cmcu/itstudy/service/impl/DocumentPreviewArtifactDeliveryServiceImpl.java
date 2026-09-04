package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.dto.document.PreviewMode;
import com.cmcu.itstudy.entity.DocumentFile;
import com.cmcu.itstudy.entity.DocumentPreviewArtifact;
import com.cmcu.itstudy.enums.AllowedDocumentFileType;
import com.cmcu.itstudy.enums.DocumentPreviewArtifactKind;
import com.cmcu.itstudy.enums.DocumentPreviewArtifactStatus;
import com.cmcu.itstudy.handle.PreviewArtifactDeliveryException;
import com.cmcu.itstudy.handle.PreviewArtifactStateRaceException;
import com.cmcu.itstudy.handle.SignedUploadTargetFailedException;
import com.cmcu.itstudy.handle.StorageObjectNotFoundException;
import com.cmcu.itstudy.repository.DocumentFileRepository;
import com.cmcu.itstudy.repository.DocumentPreviewArtifactRepository;
import com.cmcu.itstudy.repository.DocumentRepository;
import com.cmcu.itstudy.service.contract.DocumentPreviewArtifactDeliveryService;
import com.cmcu.itstudy.service.contract.SupabaseStorageService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase&nbsp;O4B artifact delivery implementation.
 *
 * <p>This service is invoked by {@code DocumentPreviewController} only
 * AFTER:</p>
 * <ol>
 *   <li>the metadata-only access decision has resolved to FULL or
 *       LIMITED (LOCKED never reaches this service);</li>
 *   <li>the artifact state service has reported READY for the
 *       matching kind;</li>
 *   <li>the original-document path has been confirmed as an Office
 *       document (the legacy non-Office PDF path still routes
 *       through {@code PaidDocumentPreviewService.buildPreview}).</li>
 * </ol>
 *
 * <h2>Pipeline</h2>
 * <ol>
 *   <li>Resolve the primary {@link DocumentFile} for the document id.</li>
 *   <li>Resolve the READY artifact whose kind matches the authorized
 *       mode. FULL → FULL; LIMITED → LIMITED.</li>
 *   <li>Re-verify {@code artifact.status == READY} at read time so a
 *       race against the worker (DEAD during processing) cannot
 *       leak bytes.</li>
 *   <li>Re-verify {@code artifact.storageBucket} and
 *       {@code artifact.storagePath} are non-blank.</li>
 *   <li>Download bytes from the artifact's bucket/path via
 *       {@code SupabaseStorageService.downloadPrivateObject}. The
 *       original-document bucket/path is NEVER passed to the storage
 *       service in this flow.</li>
 *   <li>Verify the bytes start with the {@code %PDF-} magic.</li>
 *   <li>Verify the PDF page count is positive.</li>
 *   <li>Return the artifact row, the bytes, and the page count.</li>
 * </ol>
 *
 * <h2>Safety</h2>
 * <ul>
 *   <li>This service NEVER invokes LibreOffice or POI conversion
 *       paths &mdash; preview requests never trigger Office
 *       conversion.</li>
 *   <li>The original {@code DocumentFile.bucket/path} is NEVER used
 *       as the storage source for an Office preview request.</li>
 *   <li>Storage IO is outside any database transaction (the storage
 *       service contract enforces this).</li>
 *   <li>Supabase credentials, Authorization headers, and signed URLs
 *       are never logged.</li>
 * </ul>
 */
@Service
public class DocumentPreviewArtifactDeliveryServiceImpl
        implements DocumentPreviewArtifactDeliveryService {

    private static final byte[] PDF_MAGIC = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D};

    private final DocumentRepository documentRepository;
    private final DocumentFileRepository documentFileRepository;
    private final DocumentPreviewArtifactRepository artifactRepository;
    private final SupabaseStorageService storageService;

    public DocumentPreviewArtifactDeliveryServiceImpl(
            DocumentRepository documentRepository,
            DocumentFileRepository documentFileRepository,
            DocumentPreviewArtifactRepository artifactRepository,
            SupabaseStorageService storageService) {
        this.documentRepository = Objects.requireNonNull(documentRepository);
        this.documentFileRepository = Objects.requireNonNull(documentFileRepository);
        this.artifactRepository = Objects.requireNonNull(artifactRepository);
        this.storageService = Objects.requireNonNull(storageService);
    }

    @Override
    public DeliveredArtifact deliverReadyArtifact(
            UUID documentId, PreviewMode authorizedMode) {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(authorizedMode, "authorizedMode");

        if (authorizedMode == PreviewMode.LOCKED) {
            throw new IllegalArgumentException(
                    "LOCKED is not a valid authorized mode for READY delivery");
        }

        DocumentPreviewArtifactKind targetKind = targetKind(authorizedMode);

        // Resolve the artifact inside a short read-only transaction.
        // The storage download MUST happen outside this transaction
        // per the SupabaseStorageService contract.
        ResolvedArtifact resolved;
        try {
            resolved = resolveArtifact(documentId, targetKind);
        } catch (DocumentArtifactNotReadyException race) {
            // The artifact row's status changed between the
            // controller's READY check and our read-time re-check.
            // Surface as a state race so the controller can re-query
            // and emit the actual current waiting/dead state.
            throw new PreviewArtifactStateRaceException(
                    "Artifact state changed at read time for document "
                            + documentId + " (kind=" + targetKind + ")",
                    race);
        } catch (TerminalDeliveryException tde) {
            // The artifact row is well-formed but its storage
            // contract is broken. Terminal delivery error; no retry.
            throw new PreviewArtifactDeliveryException(tde.getMessage(), tde);
        }

        // Outside-transaction storage IO.
        byte[] pdfBytes;
        try {
            pdfBytes = storageService.downloadPrivateObject(
                    resolved.bucket, resolved.path);
        } catch (StorageObjectNotFoundException e) {
            // Storage object missing after READY: terminal delivery
            // error. We do NOT turn this into a waiting state.
            throw new PreviewArtifactDeliveryException(
                    "Artifact storage object missing for document "
                            + documentId + " (kind=" + targetKind + ")", e);
        } catch (SignedUploadTargetFailedException e) {
            // Transport failure: let the controller decide whether
            // to surface a retryable state. We do NOT swallow it as
            // a waiting state inside the service.
            throw e;
        }
        // Any other RuntimeException propagates to the global
        // exception handler — never disguised as PENDING.
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new PreviewArtifactDeliveryException(
                    "Artifact storage returned empty body for document "
                            + documentId + " (kind=" + targetKind + ")");
        }
        verifyPdfMagic(pdfBytes, documentId, resolved.artifact.getId());

        int pageCount = safeCountPages(pdfBytes);
        if (pageCount <= 0) {
            throw new PreviewArtifactDeliveryException(
                    "Artifact PDF has zero pages for document "
                            + documentId + " (kind=" + targetKind + ")");
        }

        return new DeliveredArtifact(resolved.artifact, pdfBytes, pageCount);
    }

    /**
     * Resolves the artifact row, primary file id, and bucket/path
     * inside a short read-only transaction. The returned record
     * carries everything the storage step needs.
     *
     * <p>This method is split out so the storage download runs OUTSIDE
     * the transaction, per the {@link SupabaseStorageService}
     * contract.</p>
     *
     * @throws DocumentArtifactNotReadyException when status is not READY
     * @throws TerminalDeliveryException when the artifact row is
     *         well-formed but has blank bucket / wrong kind / is
     *         missing entirely
     */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    protected ResolvedArtifact resolveArtifact(
            UUID documentId, DocumentPreviewArtifactKind targetKind) {
        boolean documentExists =
                documentRepository.findById(documentId).isPresent();
        if (!documentExists) {
            throw new TerminalDeliveryException(
                    "Document not found for READY delivery: " + documentId);
        }

        DocumentFile primaryFile = documentFileRepository
                .findByDocumentIdAndPrimaryTrue(documentId)
                .orElseThrow(() -> new TerminalDeliveryException(
                        "No primary DocumentFile for " + documentId));
        AllowedDocumentFileType fileType =
                AllowedDocumentFileType.fromExtension(primaryFile.getFileExtension())
                        .orElse(null);
        boolean isOffice = fileType == AllowedDocumentFileType.DOC
                || fileType == AllowedDocumentFileType.DOCX;
        if (!isOffice) {
            throw new TerminalDeliveryException(
                    "deliverReadyArtifact called for non-Office document "
                            + documentId);
        }

        Optional<DocumentPreviewArtifact> maybeArtifact =
                artifactRepository
                        .findFirstByDocumentFileIdAndArtifactKindOrderByCreatedAtDescIdDesc(
                                primaryFile.getId(), targetKind);
        DocumentPreviewArtifact artifact = maybeArtifact.orElseThrow(() ->
                new TerminalDeliveryException(
                        "No READY artifact found for document " + documentId
                                + " and kind " + targetKind));

        if (artifact.getStatus() != DocumentPreviewArtifactStatus.READY) {
            // The state is no longer READY — this is a race.
            throw new DocumentArtifactNotReadyException(
                    "Artifact " + artifact.getId()
                            + " is not READY (status=" + artifact.getStatus() + ")");
        }
        if (artifact.getArtifactKind() != targetKind) {
            throw new TerminalDeliveryException(
                    "Artifact kind " + artifact.getArtifactKind()
                            + " does not match the authorized kind " + targetKind);
        }
        if (!StringUtils.hasText(artifact.getStorageBucket())
                || !StringUtils.hasText(artifact.getStoragePath())) {
            throw new TerminalDeliveryException(
                    "Artifact " + artifact.getId()
                            + " has blank storageBucket/storagePath");
        }

        return new ResolvedArtifact(artifact,
                artifact.getStorageBucket(), artifact.getStoragePath());
    }

    private static DocumentPreviewArtifactKind targetKind(PreviewMode authorizedMode) {
        switch (authorizedMode) {
            case FULL:
                return DocumentPreviewArtifactKind.FULL;
            case LIMITED:
                return DocumentPreviewArtifactKind.LIMITED;
            default:
                throw new IllegalArgumentException(
                        "Unsupported authorized mode for READY delivery: "
                                + authorizedMode);
        }
    }

    private static void verifyPdfMagic(byte[] bytes, UUID documentId, UUID artifactId) {
        if (bytes.length < PDF_MAGIC.length) {
            throw new PreviewArtifactDeliveryException(
                    "Artifact bytes for document " + documentId
                            + " artifact " + artifactId
                            + " do not start with %PDF- magic");
        }
        for (int i = 0; i < PDF_MAGIC.length; i++) {
            if (bytes[i] != PDF_MAGIC[i]) {
                throw new PreviewArtifactDeliveryException(
                        "Artifact bytes for document " + documentId
                                + " artifact " + artifactId
                                + " do not start with %PDF- magic");
            }
        }
    }

    private static int safeCountPages(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return document.getNumberOfPages();
        } catch (IOException ioe) {
            // PDFBox failed to parse the bytes — this is a terminal
            // delivery error. We deliberately do NOT silently return
            // 0 here for callers that branch on the return value.
            throw new PreviewArtifactDeliveryException(
                    "Artifact bytes failed PDFBox parse: " + ioe.getMessage(),
                    ioe);
        }
    }

    /**
     * Bundle produced by {@link #resolveArtifact(UUID,
     * DocumentPreviewArtifactKind)}. The artifact row, its
     * non-blank bucket, and its non-blank path are captured together
     * so the storage step can run outside the DB transaction.
     */
    protected static final class ResolvedArtifact {
        final DocumentPreviewArtifact artifact;
        final String bucket;
        final String path;

        ResolvedArtifact(DocumentPreviewArtifact artifact, String bucket, String path) {
            this.artifact = artifact;
            this.bucket = bucket;
            this.path = path;
        }
    }

    /**
     * Internal marker for state races inside {@link #resolveArtifact}.
     * Translated at the boundary to
     * {@link com.cmcu.itstudy.handle.PreviewArtifactStateRaceException}.
     */
    private static final class DocumentArtifactNotReadyException extends RuntimeException {
        DocumentArtifactNotReadyException(String message) {
            super(message);
        }
    }

    /**
     * Internal marker for terminal delivery errors inside
     * {@link #resolveArtifact}. Translated at the boundary to
     * {@link com.cmcu.itstudy.handle.PreviewArtifactDeliveryException}.
     */
    private static final class TerminalDeliveryException extends RuntimeException {
        TerminalDeliveryException(String message) {
            super(message);
        }
    }
}