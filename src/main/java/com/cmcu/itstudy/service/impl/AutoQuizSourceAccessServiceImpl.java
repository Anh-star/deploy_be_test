package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.dto.autoquiz.AutoQuizSourceResolutionDto;
import com.cmcu.itstudy.dto.autoquiz.AutoQuizSourceResolutionDto.SourceKind;
import com.cmcu.itstudy.entity.DocumentFile;
import com.cmcu.itstudy.entity.DocumentPreviewArtifact;
import com.cmcu.itstudy.entity.QuizGeneration;
import com.cmcu.itstudy.enums.AllowedDocumentFileType;
import com.cmcu.itstudy.enums.DocumentPreviewArtifactKind;
import com.cmcu.itstudy.enums.DocumentPreviewArtifactStatus;
import com.cmcu.itstudy.enums.QuizGenerationStatus;
import com.cmcu.itstudy.handle.AutoQuizSourceAccessDeniedException;
import com.cmcu.itstudy.handle.AutoQuizSourceAccessDeniedException.Reason;
import com.cmcu.itstudy.repository.DocumentPreviewArtifactRepository;
import com.cmcu.itstudy.repository.QuizGenerationRepository;
import com.cmcu.itstudy.service.contract.AutoQuizSourceAccessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 2E-A default implementation.
 *
 * <p>The implementation is deliberately split into a
 * @Transactional(readOnly = true) resolution step followed by NO
 * storage IO: the controller does the byte download via
 * SupabaseStorageService.downloadPrivateObject so the storage
 * round-trip runs OUTSIDE any database transaction (per the
 * SupabaseStorageService contract).</p>
 *
 * <h2>Dispatch-token comparison</h2>
 * <p>The supplied dispatch token is compared against
 * QuizGeneration.dispatchToken using a constant-time loop
 * (constantTimeEquals(UUID, UUID)) so an attacker cannot probe the
 * expected token one byte at a time.</p>
 *
 * <h2>Document / file linkage</h2>
 * <p>The endpoint URL does NOT carry a documentFileId path segment:
 * the server-side source is the QuizGeneration.documentFile row
 * that the dispatcher bound to the generation during the Phase 2D
 * claim. Any divergence between the request and this bound row is
 * impossible by construction (the URL only identifies the
 * generation). If the bound documentFile is missing, the request is
 * rejected with Reason.PRIMARY_FILE_MISSING.</p>
 *
 * <h2>DOC / DOCX resolution</h2>
 * <p>For DOC / DOCX the most recent READY FULL
 * DocumentPreviewArtifact is selected
 * (DocumentPreviewArtifactRepository
 * .findFirstByDocumentFileIdAndArtifactKindOrderByCreatedAtDescIdDesc).
 * When no READY FULL artifact exists yet, the request is rejected
 * with Reason.PREVIEW_NOT_READY. The endpoint NEVER invokes
 * LibreOffice or POI; the existing async preview pipeline is the
 * only path that produces the FULL preview PDF.</p>
 */
@Service
public class AutoQuizSourceAccessServiceImpl
        implements AutoQuizSourceAccessService {

    private static final Logger log =
            LoggerFactory.getLogger(AutoQuizSourceAccessServiceImpl.class);

    private final QuizGenerationRepository generationRepository;
    private final DocumentPreviewArtifactRepository artifactRepository;

    public AutoQuizSourceAccessServiceImpl(
            QuizGenerationRepository generationRepository,
            DocumentPreviewArtifactRepository artifactRepository) {
        this.generationRepository =
                Objects.requireNonNull(generationRepository);
        this.artifactRepository =
                Objects.requireNonNull(artifactRepository);
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public AutoQuizSourceResolutionDto resolveSource(
            UUID generationId, UUID suppliedDispatchToken) {
        if (generationId == null) {
            throw new AutoQuizSourceAccessDeniedException(
                    Reason.GENERATION_NOT_FOUND,
                    "Generation not found");
        }
        if (suppliedDispatchToken == null) {
            throw new AutoQuizSourceAccessDeniedException(
                    Reason.MISSING_TOKEN,
                    "Dispatch token is required");
        }

        QuizGeneration generation = generationRepository
                .findById(generationId)
                .orElseThrow(() -> new AutoQuizSourceAccessDeniedException(
                        Reason.GENERATION_NOT_FOUND,
                        "Generation not found"));

        // Status gate. The endpoint is ONLY valid for PROCESSING. Any
        // other status (QUEUED, WAITING_SOURCE, READY, FAILED,
        // CANCELLED) is rejected. CANCELLED / FAILED / READY are
        // terminal and must never expose bytes. QUEUED means the
        // dispatch has not started. WAITING_SOURCE means the source
        // PDF is not yet AI-readable.
        if (generation.getStatus() != QuizGenerationStatus.PROCESSING) {
            throw new AutoQuizSourceAccessDeniedException(
                    Reason.STATUS_NOT_PROCESSING,
                    "Generation is not in PROCESSING state");
        }

        // Token gate. Constant-time comparison so timing cannot be
        // used to probe the expected value. A blank stored token is
        // a system bug; we still reject the request.
        UUID expectedToken = generation.getDispatchToken();
        if (expectedToken == null
                || !constantTimeEquals(expectedToken, suppliedDispatchToken)) {
            // We deliberately do NOT include the generationId, the
            // expected token, or the supplied token in the exception
            // message. The caller already knows both.
            throw new AutoQuizSourceAccessDeniedException(
                    Reason.TOKEN_MISMATCH,
                    "Dispatch token does not match this generation");
        }

        DocumentFile primaryFile = generation.getDocumentFile();
        if (primaryFile == null) {
            // The generation row is bound to a documentFile at
            // insert time; a NULL here means data corruption.
            throw new AutoQuizSourceAccessDeniedException(
                    Reason.PRIMARY_FILE_MISSING,
                    "Generation has no associated primary file");
        }

        AllowedDocumentFileType fileType =
                AllowedDocumentFileType.fromExtension(
                                primaryFile.getFileExtension())
                        .orElse(null);
        if (fileType == null) {
            throw new AutoQuizSourceAccessDeniedException(
                    Reason.UNSUPPORTED_FILE_TYPE,
                    "Source file type is not supported");
        }

        switch (fileType) {
            case PDF:
                return resolvePdfOriginal(primaryFile);
            case DOC:
            case DOCX:
                return resolveFullDocPreview(primaryFile);
            case PPT:
            case PPTX:
            default:
                throw new AutoQuizSourceAccessDeniedException(
                        Reason.UNSUPPORTED_FILE_TYPE,
                        "Source file type is not supported");
        }
    }

    /**
     * Resolve the storage descriptor for a PDF original. Bytes come
     * straight from the primary DocumentFile's private Supabase
     * bucket/path.
     *
     * @throws AutoQuizSourceAccessDeniedException with
     *         Reason.STORAGE_NOT_CONFIGURED when the bucket or path
     *         is blank.
     */
    private AutoQuizSourceResolutionDto resolvePdfOriginal(
            DocumentFile primaryFile) {
        String bucket = primaryFile.getStorageBucket();
        String path = primaryFile.getStoragePath();
        if (!StringUtils.hasText(bucket)
                || !StringUtils.hasText(path)) {
            throw new AutoQuizSourceAccessDeniedException(
                    Reason.STORAGE_NOT_CONFIGURED,
                    "Source PDF is not linked to storage");
        }
        log.info(
                "Auto Quiz source access: generation=pdf_original "
                        + "documentFileId={} bytesBucket={} bytesPath={}",
                primaryFile.getId(),
                bucket,
                path);
        return new AutoQuizSourceResolutionDto(
                bucket, path,
                AutoQuizSourceResolutionDto.EXTENSION_PDF,
                AutoQuizSourceResolutionDto.CONTENT_TYPE_PDF,
                SourceKind.PDF_ORIGINAL);
    }

    /**
     * Resolve the storage descriptor for a DOC / DOCX. Bytes come
     * from the most recent READY FULL DocumentPreviewArtifact for
     * the DocumentFile.
     *
     * <p>If no READY FULL artifact exists yet, the request is
     * rejected with Reason.PREVIEW_NOT_READY. LibreOffice / POI are
     * NEVER invoked at request time.</p>
     *
     * @throws AutoQuizSourceAccessDeniedException with
     *         Reason.PREVIEW_NOT_READY or
     *         Reason.STORAGE_NOT_CONFIGURED
     */
    private AutoQuizSourceResolutionDto resolveFullDocPreview(
            DocumentFile primaryFile) {
        Optional<DocumentPreviewArtifact> maybeArtifact = artifactRepository
                .findFirstByDocumentFileIdAndArtifactKindOrderByCreatedAtDescIdDesc(
                        primaryFile.getId(),
                        DocumentPreviewArtifactKind.FULL);
        DocumentPreviewArtifact artifact = maybeArtifact.orElseThrow(
                () -> new AutoQuizSourceAccessDeniedException(
                        Reason.PREVIEW_NOT_READY,
                        "FULL preview artifact is not ready"));

        if (artifact.getStatus() != DocumentPreviewArtifactStatus.READY) {
            // The most recent FULL artifact exists but its status is
            // not READY; the async preview pipeline is still
            // working. Reject the request; n8n should retry.
            throw new AutoQuizSourceAccessDeniedException(
                    Reason.PREVIEW_NOT_READY,
                    "FULL preview artifact is not ready");
        }
        String bucket = artifact.getStorageBucket();
        String path = artifact.getStoragePath();
        if (!StringUtils.hasText(bucket)
                || !StringUtils.hasText(path)) {
            throw new AutoQuizSourceAccessDeniedException(
                    Reason.STORAGE_NOT_CONFIGURED,
                    "FULL preview artifact is not linked to storage");
        }
        log.info(
                "Auto Quiz source access: generation=doc_full_preview "
                        + "documentFileId={} artifactId={} bytesBucket={} "
                        + "bytesPath={}",
                primaryFile.getId(),
                artifact.getId(),
                bucket,
                path);
        return new AutoQuizSourceResolutionDto(
                bucket, path,
                AutoQuizSourceResolutionDto.EXTENSION_PDF,
                AutoQuizSourceResolutionDto.CONTENT_TYPE_PDF,
                SourceKind.DOC_PREVIEW_FULL);
    }

    /**
     * Constant-time UUID comparison. Loops over the high/low long
     * words of both UUIDs and combines bit-difference via OR so the
     * runtime is independent of where the first mismatch occurs.
     *
     * <p>This avoids the classic string-comparison timing side
     * channel where String.equals returns as soon as a differing
     * byte is found.</p>
     */
    static boolean constantTimeEquals(UUID expected, UUID supplied) {
        if (expected == null || supplied == null) {
            return false;
        }
        long diff = 0L;
        diff |= expected.getMostSignificantBits()
                ^ supplied.getMostSignificantBits();
        diff |= expected.getLeastSignificantBits()
                ^ supplied.getLeastSignificantBits();
        return diff == 0L;
    }
}
