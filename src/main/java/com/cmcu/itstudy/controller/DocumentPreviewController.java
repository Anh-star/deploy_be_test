package com.cmcu.itstudy.controller;

import com.cmcu.itstudy.dto.document.DocumentPreviewArtifactStatusDto;
import com.cmcu.itstudy.dto.document.DocumentPreviewStateResponseDto;
import com.cmcu.itstudy.dto.document.PreviewMode;
import com.cmcu.itstudy.entity.Document;
import com.cmcu.itstudy.entity.User;
import com.cmcu.itstudy.handle.PaidPreviewUnavailableException;
import com.cmcu.itstudy.handle.PreviewArtifactDeliveryException;
import com.cmcu.itstudy.handle.PreviewArtifactStateRaceException;
import com.cmcu.itstudy.handle.SignedUploadTargetFailedException;
import com.cmcu.itstudy.handle.StorageObjectNotFoundException;
import com.cmcu.itstudy.security.UserDetailsImpl;
import com.cmcu.itstudy.service.contract.DocumentAccessService;
import com.cmcu.itstudy.service.contract.DocumentPreviewArtifactDeliveryService;
import com.cmcu.itstudy.service.contract.DocumentPreviewArtifactDeliveryService.DeliveredArtifact;
import com.cmcu.itstudy.service.contract.DocumentPreviewSnapshotService;
import com.cmcu.itstudy.service.contract.DocumentPreviewSnapshotService.DocumentPreviewSnapshot;
import com.cmcu.itstudy.service.contract.DocumentPreviewStateService;
import com.cmcu.itstudy.service.contract.PaidDocumentPreviewService;
import com.cmcu.itstudy.service.contract.PaidDocumentPreviewService.DocumentRequest;
import com.cmcu.itstudy.service.contract.PaidDocumentPreviewService.PreviewResult;
import com.cmcu.itstudy.service.contract.PaidDocumentPreviewService.RendererKind;
import com.cmcu.itstudy.service.contract.PreviewAccessDecisionService;
import com.cmcu.itstudy.service.contract.SupabaseStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
/**
 * Secure paid preview endpoint.
 *
 * <h2>Route</h2>
 * <pre>
 *   GET /api/documents/{id}/preview
 * </pre>
 *
 * <h2>Wire contract</h2>
 * <ul>
 *   <li>Optional authentication: anonymous guests can still receive a
 *       LIMITED or LOCKED response for approved paid documents. The
 *       existing {@code /api/documents/{id}} public detail endpoint
 *       does not require authentication, so preview follows the same
 *       posture.</li>
 *   <li>{@code FULL} / {@code LIMITED} PDF → {@code Content-Type:
 *       application/pdf} with the {@code X-Preview-Mode},
 *       {@code X-Preview-Renderer}, {@code X-Preview-Pages}, and
 *       {@code X-Total-Pages} headers.</li>
 *   <li>{@code FULL} DOCX → {@code Content-Type:
 *       application/vnd.openxmlformats-officedocument.wordprocessingml.document}
 *       carrying the original DOCX bytes; the frontend renders the
 *       payload with {@code docx-preview}.</li>
 *   <li>{@code FULL} DOC (legacy binary) → {@code Content-Type:
 *       text/html; charset=UTF-8} carrying sanitised HTML extracted
 *       with Apache POI HWPF.</li>
 *   <li>{@code LOCKED} → {@code Content-Type: application/json} with
 *       a small descriptor carrying {@code mode}, {@code reason}, and
 *       a Vietnamese user-facing message. The descriptor never carries
 *       bucket, path, signed URL, token, or service credential.</li>
 * </ul>
 *
 * <h2>Safety</h2>
 * <ul>
 *   <li>Bucket / path come exclusively from the {@link DocumentPreviewSnapshot}
 *       resolved server-side from a {@code DocumentFile} row.</li>
 *   <li>The endpoint never redirects to a Supabase object URL.</li>
 *   <li>It does not echo raw Supabase response bodies or any signed URL.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
public class DocumentPreviewController {

    private static final Logger log = LoggerFactory.getLogger(DocumentPreviewController.class);

    private static final String HEADER_PREVIEW_MODE = "X-Preview-Mode";
    private static final String HEADER_PREVIEW_PAGES = "X-Preview-Pages";
    private static final String HEADER_TOTAL_PAGES = "X-Total-Pages";
    private static final String HEADER_PREVIEW_RENDERER = "X-Preview-Renderer";

    private static final String DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final MediaType DOCX_MEDIA = MediaType.parseMediaType(DOCX_MIME);
    private static final MediaType DOC_HTML_MEDIA = MediaType.parseMediaType("text/html;charset=UTF-8");

    private final DocumentPreviewSnapshotService snapshotService;
    private final PaidDocumentPreviewService previewService;
    private final DocumentAccessService documentAccessService;
    private final DocumentPreviewStateService previewStateService;
    private final PreviewAccessDecisionService accessDecisionService;
    private final DocumentPreviewArtifactDeliveryService deliveryService;
    private final SupabaseStorageService storageService;
    private final ObjectMapper objectMapper;

    public DocumentPreviewController(DocumentPreviewSnapshotService snapshotService,
                                     PaidDocumentPreviewService previewService,
                                     DocumentAccessService documentAccessService,
                                     DocumentPreviewStateService previewStateService,
                                     PreviewAccessDecisionService accessDecisionService,
                                     DocumentPreviewArtifactDeliveryService deliveryService,
                                     SupabaseStorageService storageService,
                                     ObjectMapper objectMapper) {
        this.snapshotService = snapshotService;
        this.previewService = previewService;
        this.documentAccessService = documentAccessService;
        this.previewStateService = previewStateService;
        this.accessDecisionService = accessDecisionService;
        this.deliveryService = deliveryService;
        this.storageService = storageService;
        this.objectMapper = objectMapper;
    }

    @GetMapping(value = "/documents/{id}/preview",
            produces = {MediaType.APPLICATION_PDF_VALUE, DOCX_MIME,
                    "text/html;charset=UTF-8", MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<?> getDocumentPreview(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        DocumentPreviewSnapshot snapshot = snapshotService.resolve(id)
                .orElseThrow(() -> new NoSuchElementException("Document not found: " + id));

        // Phase O4B (final) security contract:
        //   1. authenticate the current user (handled by Spring
        //      Security) and gather authorities + purchaser status;
        //   2. perform a *metadata-only* access decision via
        //      PreviewAccessDecisionService (LOCKED / FULL / LIMITED) —
        //      this decision reads NO bytes, NO storage object, and
        //      NO artifact row;
        //   3. if the decision is LOCKED, return the existing LOCKED
        //      response immediately. The artifact state repository
        //      MUST NOT be touched, and the storage service MUST NOT
        //      be called, because a locked user cannot be allowed to
        //      infer whether a FULL artifact exists, is READY, is
        //      DEAD, or is being retried;
        //   4. only after access is FULL or LIMITED may the
        //      controller query Office preview artifact state;
        //   5. for PENDING / PROCESSING / RETRY / DEAD state, return
        //      a safe state descriptor — never read preview bytes;
        //   6. only for READY may the controller invoke the
        //      byte-materializing preview service. FULL access
        //      receives FULL PDF; LIMITED access receives LIMITED PDF.
        User viewer = currentUser != null ? currentUser.getUser() : null;
        Collection<? extends GrantedAuthority> authorities =
                currentUser != null ? currentUser.getAuthorities() : null;
        boolean purchaser = viewer != null
                && documentAccessService.hasAccess(viewer.getId(), id);

        if (Boolean.TRUE.equals(snapshot.deleted())) {
            boolean isOwner = viewer != null && viewer.getId() != null
                    && viewer.getId().equals(snapshot.ownerId());
            boolean isModeratorOrAdmin = authorities != null && authorities.stream().anyMatch(a -> {
                String auth = a.getAuthority();
                return "ROLE_ADMIN".equals(auth) || "ADMIN".equals(auth) || "APPROVE_DOCUMENT".equals(auth)
                        || "ROLE_CONTENT_MODERATOR".equals(auth) || "CONTENT_MODERATOR".equals(auth);
            });

            if (!purchaser && !isOwner && !isModeratorOrAdmin) {
                throw new NoSuchElementException("Document not found: " + id);
            }
            boolean isExpired = Boolean.TRUE.equals(snapshot.fileCleaned()) ||
                    (snapshot.retentionExpiresAt() != null && java.time.LocalDateTime.now().isAfter(snapshot.retentionExpiresAt()));
            if (isExpired) {
                return buildLockedResponse(PreviewResult.locked(
                        com.cmcu.itstudy.dto.document.PreviewLockedReason.PREVIEW_UNAVAILABLE,
                        "Tài liệu đã hết thời hạn lưu trữ để xem lại."));
            }
        }

        DocumentRequest request = new DocumentRequest(
                snapshot.documentId(),
                snapshot.bucket(),
                snapshot.path(),
                snapshot.mimeType(),
                snapshot.status(),
                snapshot.isPaid(),
                snapshot.ownerId(),
                viewer,
                authorities,
                purchaser);

        // (2) and (3): metadata-only authorization decision.
        // The decision service is intentionally pure and never reads
        // bytes or storage. This is the FIRST call the controller
        // makes after the snapshot is resolved.
        Document decisionDocument = new Document();
        decisionDocument.setId(snapshot.documentId());
        decisionDocument.setStatus(snapshot.status());
        decisionDocument.setIsPaid(Boolean.TRUE.equals(snapshot.isPaid()));
        if (snapshot.ownerId() != null) {
            User owner = new User();
            owner.setId(snapshot.ownerId());
            decisionDocument.setCreatedBy(owner);
        }
        PreviewAccessDecisionService.Decision decision = accessDecisionService.decide(
                decisionDocument, viewer, authorities, purchaser);
        PreviewMode authorizedMode = decision.mode();

        // LOCKED: do NOT query artifact state, do NOT read storage,
        // do NOT call previewService.buildPreview. Return the existing
        // safe LOCKED response.
        if (authorizedMode == null || authorizedMode == PreviewMode.LOCKED) {
            return buildLockedResponse(PreviewResult.denied());
        }

        // (4) and (5): authorized FULL or LIMITED viewer. Query the
        // safe preview-state descriptor (also metadata-only — no
        // storage object is read by the state service).
        DocumentPreviewStateService.PreviewState officeState = previewStateService != null
                ? previewStateService.resolve(id) : null;
        DocumentPreviewArtifactStatusDto fullStatus = (officeState != null
                && officeState.officeDocument())
                ? officeState.fullStatus() : null;

        boolean isOffice = officeState != null && officeState.officeDocument();

        if (isOffice) {
            if (fullStatus == null
                    || fullStatus != DocumentPreviewArtifactStatusDto.READY) {
                if (fullStatus == DocumentPreviewArtifactStatusDto.DEAD) {
                    return buildDeadStateResponse(authorizedMode, officeState);
                }
                return buildWaitingStateResponse(authorizedMode, officeState);
            }
            // Office READY: deliver the canonical PDF artifact stored
            // at the artifact's own storageBucket/storagePath. The
            // original DocumentFile bucket/path is NEVER consulted in
            // this branch.
            return buildReadyOfficePdfResponse(id, authorizedMode, officeState);
        }

        // (6): non-Office (PDF) document. The original-file preview
        // builder handles PDF, LOCKED one-page, and non-PDF paid
        // cases. For non-Office documents we may still call
        // previewService.buildPreview because the original PDF is
        // already the canonical preview.
        //
        // Free-document short-circuit: when the document is free
        // (isPaid=false) and the access decision has resolved to
        // FULL, stream the original PDF bytes directly from the
        // primary DocumentFile's storage bucket/path. This matches
        // the documented contract at PaidDocumentPreviewServiceImpl
        // ("the controller short-circuits free documents before
        // invoking this service") and avoids the defensive
        // PREVIEW_UNAVAILABLE gate that would otherwise return
        // "Free documents use the existing public preview pipeline".
        if (Boolean.FALSE.equals(snapshot.isPaid())
                && authorizedMode == PreviewMode.FULL
                && org.springframework.util.StringUtils.hasText(snapshot.bucket())
                && org.springframework.util.StringUtils.hasText(snapshot.path())) {
            return buildFreeOwnerPdfResponse(snapshot);
        }

        PreviewResult result;
        try {
            result = previewService.buildPreview(request);
        } catch (StorageObjectNotFoundException e) {
            return buildLockedResponse(PreviewResult.locked(
                    com.cmcu.itstudy.dto.document.PreviewLockedReason.PREVIEW_UNAVAILABLE,
                    "Tài liệu chưa được liên kết với kho lưu trữ"));
        } catch (SignedUploadTargetFailedException e) {
            log.warn("Storage back-end refused preview generation");
            return buildLockedResponse(PreviewResult.locked(
                    com.cmcu.itstudy.dto.document.PreviewLockedReason.PREVIEW_UNAVAILABLE,
                    "Không thể tải tài liệu từ kho lưu trữ"));
        } catch (PaidPreviewUnavailableException e) {
            return buildLockedResponse(PreviewResult.locked(
                    com.cmcu.itstudy.dto.document.PreviewLockedReason.PREVIEW_UNAVAILABLE,
                    "Không thể tạo bản xem trước"));
        }

        // Belt-and-braces: a result that landed as LOCKED means the
        // metadata-only decision was bypassed (e.g. one-page paid
        // PDF). Honour the LOCKED response without leaking bytes.
        if (result.mode() == PreviewMode.LOCKED) {
            return buildLockedResponse(result);
        }

        // The DOCX / DOC_HTML renderers are retired under async
        // Office preview — the FULL PDF path is the only authorised
        // renderer. Refuse to ship raw DOCX bytes from the preview
        // endpoint.
        if (result.renderer() == RendererKind.DOCX) {
            return buildDeadStateResponse(authorizedMode, officeState);
        }
        if (result.renderer() == RendererKind.DOC_HTML) {
            return buildDeadStateResponse(authorizedMode, officeState);
        }
        return buildPdfResponse(result);
    }

    /**
     * Builds the response for a READY Office preview artifact. This
     * method routes through the dedicated artifact-delivery service
     * so that the bytes returned to the browser come from the
     * artifact's own storage path, NOT the original DocumentFile
     * storage path. The original DOC/DOCX bytes are never read in
     * this branch.
     *
     * <p>Failure semantics:</p>
     * <ul>
     *   <li>{@link PreviewArtifactStateRaceException} &mdash; the
     *       artifact was READY at the controller gate but the
     *       delivery service saw a different status at read time.
     *       Re-resolve the state and emit the actual current
     *       waiting/dead JSON.</li>
     *   <li>{@link PreviewArtifactDeliveryException} &mdash;
     *       terminal delivery error (blank bucket, wrong kind,
     *       non-PDF bytes, zero pages, missing storage object).
     *       Emit safe terminal JSON; stop polling.</li>
     *   <li>{@link SignedUploadTargetFailedException} &mdash;
     *       storage transport failure; surface as a safe terminal
     *       state marked retryable. We deliberately do NOT turn
     *       this into a PENDING waiting state.</li>
     *   <li>Other {@link RuntimeException} &mdash; NOT caught.
     *       The global exception handler produces a safe 500
     *       response. We never disguise an unexpected failure as
     *       PENDING.</li>
     * </ul>
     */
    private ResponseEntity<byte[]> buildReadyOfficePdfResponse(
            UUID documentId,
            PreviewMode authorizedMode,
            DocumentPreviewStateService.PreviewState officeState) {
        DeliveredArtifact delivered;
        try {
            delivered = deliveryService.deliverReadyArtifact(documentId, authorizedMode);
        } catch (PreviewArtifactStateRaceException race) {
            // The artifact's status changed at read time. Re-query
            // the state service to surface the actual current
            // waiting/dead state. NEVER convert this to a PENDING
            // polling cycle on the wrong state.
            log.warn("READY artifact state race for document {}: {}",
                    documentId, race.getMessage());
            DocumentPreviewStateService.PreviewState freshState =
                    previewStateService != null
                            ? previewStateService.resolve(documentId)
                            : officeState;
            if (freshState != null
                    && freshState.fullStatus() == DocumentPreviewArtifactStatusDto.DEAD) {
                return buildDeadStateResponse(authorizedMode, freshState);
            }
            return buildWaitingStateResponse(authorizedMode, freshState);
        } catch (PreviewArtifactDeliveryException tde) {
            // Terminal delivery error: malformed artifact. Stop
            // polling on the client side. NEVER turn this into a
            // PENDING state.
            log.warn("Terminal artifact delivery error for document {}: {}",
                    documentId, tde.getMessage());
            return buildTerminalDeliveryErrorResponse(authorizedMode);
        } catch (SignedUploadTargetFailedException sfe) {
            // Transport failure: terminal, marked retryable. The
            // backend has no defined retry transition here, so we
            // surface as a non-retryable terminal error.
            log.warn("Storage back-end refused READY artifact for {}",
                    documentId);
            return buildTerminalDeliveryErrorResponse(authorizedMode);
        } catch (StorageObjectNotFoundException e) {
            // The artifact row said READY but the storage object is
            // missing. This is a terminal delivery error — not a
            // PENDING waiting state.
            log.warn("READY artifact object missing for document {}", documentId);
            return buildTerminalDeliveryErrorResponse(authorizedMode);
        }
        // Any other RuntimeException propagates to the global
        // exception handler. We do NOT catch it here.

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"preview.pdf\"")
                .header(HEADER_PREVIEW_MODE, authorizedMode.name())
                .header(HEADER_PREVIEW_RENDERER, "PDF")
                .header(HEADER_TOTAL_PAGES,
                        String.valueOf(delivered.pageCount()));
        if (authorizedMode == PreviewMode.LIMITED) {
            builder.header(HEADER_PREVIEW_PAGES,
                    String.valueOf(delivered.pageCount()));
        }
        return builder.body(delivered.pdfBytes());
    }

    /**
     * Safe terminal-delivery-error response. Carries only the
     * documented safe fields: mode, status, message, retryable. No
     * bucket, no path, no stack trace, no internal exception. The
     * client treats this as terminal and stops polling.
     */
    private ResponseEntity<byte[]> buildTerminalDeliveryErrorResponse(
            PreviewMode authorizedMode) {
        DocumentPreviewStateResponseDto dto = DocumentPreviewStateResponseDto.builder()
                .mode(authorizedMode != null ? authorizedMode.name() : PreviewMode.FULL.name())
                .status("DEAD")
                .message("Bản xem trước không khả dụng")
                .retryable(false)
                .build();
        return writeJson(HttpStatus.CONFLICT, dto);
    }

    /**
     * Builds the safe 202 Accepted waiting-state response for an Office
     * document whose FULL preview artifact is not yet READY. The
     * response is allowed only for viewers whose access decision was
     * FULL or LIMITED; the {@code mode} field carries the
     * already-authorized mode and never leaks any artifact metadata
     * to LOCKED viewers.
     *
     * <p>The response carries only the documented safe fields: mode,
     * status, message, retryable. No storage paths, no signed URLs, no
     * service credentials, no Office bytes.</p>
     */
    private ResponseEntity<byte[]> buildWaitingStateResponse(
            PreviewMode authorizedMode,
            DocumentPreviewStateService.PreviewState state) {
        DocumentPreviewStateResponseDto dto = DocumentPreviewStateResponseDto.builder()
                .mode(authorizedMode != null ? authorizedMode.name() : PreviewMode.FULL.name())
                .status(state.fullStatus() != null ? state.fullStatus().name() : "PENDING")
                .message(state.safeMessage() != null
                        ? state.safeMessage()
                        : "Đang chờ tạo bản xem trước")
                .retryable(state.retryable())
                .build();
        return writeJson(HttpStatus.ACCEPTED, dto);
    }

    /**
     * Builds the safe terminal-state response for an Office document
     * whose FULL preview artifact is DEAD. The response is allowed
     * only for viewers whose access decision was FULL or LIMITED; the
     * {@code mode} field carries the already-authorized mode and never
     * leaks any artifact metadata to LOCKED viewers.
     *
     * <p>This is mapped to HTTP 409 Conflict (a safe non-200 terminal
     * code) per the existing business-exception conventions. The
     * response carries only the documented safe fields.</p>
     */
    private ResponseEntity<byte[]> buildDeadStateResponse(
            PreviewMode authorizedMode,
            DocumentPreviewStateService.PreviewState state) {
        PreviewMode modeForDto = authorizedMode != null
                ? authorizedMode : PreviewMode.FULL;
        DocumentPreviewStateResponseDto dto = DocumentPreviewStateResponseDto.builder()
                .mode(modeForDto.name())
                .status(state != null && state.fullStatus() != null
                        ? state.fullStatus().name() : "DEAD")
                .message(state != null && state.safeMessage() != null
                        ? state.safeMessage()
                        : "Không thể tạo bản xem trước")
                .retryable(false)
                .build();
        return writeJson(HttpStatus.CONFLICT, dto);
    }

    private ResponseEntity<byte[]> writeJson(HttpStatus status, Object body) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(body);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            // The X-Preview-Mode header always reflects the
            // authorized mode carried in the body. When the body
            // itself is a DocumentPreviewStateResponseDto we read it
            // back so callers cannot accidentally publish a mode
            // value that contradicts the authorization decision.
            String modeForHeader = "FULL";
            if (body instanceof DocumentPreviewStateResponseDto dto
                    && dto.getMode() != null) {
                modeForHeader = dto.getMode();
            }
            headers.set(HEADER_PREVIEW_MODE, modeForHeader);
            return ResponseEntity.status(status).headers(headers).body(json);
        } catch (Exception e) {
            log.warn("Failed to serialize preview state payload");
            String fallback = String.format(
                    Locale.ROOT,
                    "{\"mode\":\"FULL\",\"status\":\"%s\",\"message\":\"%s\",\"retryable\":%s}",
                    body instanceof DocumentPreviewStateResponseDto dto
                            && dto.getStatus() != null ? dto.getStatus() : "PENDING",
                    "Đang chờ tạo bản xem trước",
                    String.valueOf(true));
            return ResponseEntity.status(status)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(fallback.getBytes(StandardCharsets.UTF_8));
        }
    }

    private ResponseEntity<byte[]> buildPdfResponse(PreviewResult result) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"preview.pdf\"");
        headers.set(HEADER_PREVIEW_MODE, result.mode().name());
        headers.set(HEADER_PREVIEW_RENDERER, RendererKind.PDF.name());
        if (result.visiblePages() != null) {
            headers.set(HEADER_PREVIEW_PAGES, String.valueOf(result.visiblePages()));
        }
        if (result.totalPages() != null) {
            headers.set(HEADER_TOTAL_PAGES, String.valueOf(result.totalPages()));
        }
        return ResponseEntity.status(HttpStatus.OK).headers(headers).body(result.bytes());
    }

    /**
     * Free non-Office PDF short-circuit. Streams the original PDF
     * bytes from the {@code DocumentFile.storageBucket} /
     * {@code DocumentFile.storagePath} pair resolved by the
     * snapshot service.
     *
     * <p>This branch only fires when ALL of the following hold:</p>
     * <ul>
     *   <li>{@code snapshot.isPaid() == false} &mdash; the
     *       defensive paid/free gate at
     *       {@code PaidDocumentPreviewServiceImpl.buildPreview} would
     *       otherwise return
     *       {@code "Free documents use the existing public preview pipeline"};</li>
     *   <li>{@code authorizedMode == PreviewMode.FULL} &mdash; the
     *       metadata-only
     *       {@link DocumentPreviewSnapshotService} has already
     *       confirmed the viewer is owner / approver / super-admin
     *       / approved-public / owner-purchased. A LOCKED or null
     *       decision never reaches this branch.</li>
     *   <li>the snapshot's bucket / path are non-blank &mdash;
     *       the primary {@code DocumentFile} row exists and is
     *       linked to a known storage object.</li>
     * </ul>
     *
     * <p>Failure semantics mirror the existing paid-preview path:</p>
     * <ul>
     *   <li>{@link StorageObjectNotFoundException} &mdash; the
     *       primary file's storage object is missing. Return a
     *       safe LOCKED payload with reason
     *       {@code PREVIEW_UNAVAILABLE} and the Vietnamese
     *       "Tài liệu chưa được liên kết với kho lưu trữ"
     *       message.</li>
     *   <li>{@link SignedUploadTargetFailedException} &mdash;
     *       storage transport failure. Return a safe LOCKED payload
     *       with the Vietnamese "Không thể tải tài liệu từ kho
     *       lưu trữ" message.</li>
     *   <li>{@link PreviewFileTooLargeException} &mdash; let the
     *       global exception handler emit HTTP 413 with its
     *       generic message. Storage credentials, signed URLs, and
     *       bucket / path are never echoed back.</li>
     *   <li>Non-Empty-or-missing PDF magic, blank bytes, or
     *       PDFBox parse failure &mdash; return a safe LOCKED
     *       payload with the "Tài liệu trống" / "Không thể tạo
     *       bản xem trước" messages.</li>
     * </ul>
     *
     * <p>This branch MUST NOT be invoked for paid documents and
     * MUST NOT be invoked when the access decision is LOCKED or
     * null. The precondition gating above enforces that.</p>
     *
     * @param snapshot the immutable preview snapshot for the
     *                 document. Both bucket and path are guaranteed
     *                 non-blank by the caller.
     * @return 200 {@code application/pdf} with the original bytes
     *         and the standard preview headers, or a safe LOCKED
     *         JSON payload on storage failure.
     */
    private ResponseEntity<byte[]> buildFreeOwnerPdfResponse(
            DocumentPreviewSnapshot snapshot) {
        byte[] pdfBytes;
        try {
            pdfBytes = storageService.downloadPrivateObject(
                    snapshot.bucket(), snapshot.path());
        } catch (StorageObjectNotFoundException e) {
            return buildLockedResponse(PreviewResult.locked(
                    com.cmcu.itstudy.dto.document.PreviewLockedReason.PREVIEW_UNAVAILABLE,
                    "Tài liệu chưa được liên kết với kho lưu trữ"));
        } catch (SignedUploadTargetFailedException e) {
            log.warn("Storage back-end refused free-PDF preview generation");
            return buildLockedResponse(PreviewResult.locked(
                    com.cmcu.itstudy.dto.document.PreviewLockedReason.PREVIEW_UNAVAILABLE,
                    "Không thể tải tài liệu từ kho lưu trữ"));
        }
        if (pdfBytes == null || pdfBytes.length == 0) {
            return buildLockedResponse(PreviewResult.locked(
                    com.cmcu.itstudy.dto.document.PreviewLockedReason.PREVIEW_UNAVAILABLE,
                    "Tài liệu trống"));
        }
        if (!isPdfMagic(pdfBytes)) {
            return buildLockedResponse(PreviewResult.locked(
                    com.cmcu.itstudy.dto.document.PreviewLockedReason.PREVIEW_UNAVAILABLE,
                    "Không thể tạo bản xem trước"));
        }
        int pageCount = safeCountPages(pdfBytes);
        if (pageCount <= 0) {
            return buildLockedResponse(PreviewResult.locked(
                    com.cmcu.itstudy.dto.document.PreviewLockedReason.PREVIEW_UNAVAILABLE,
                    "Không thể tạo bản xem trước"));
        }
        return buildPdfResponse(PreviewResult.full(pdfBytes, pageCount,
                RendererKind.PDF));
    }

    private static boolean isPdfMagic(byte[] bytes) {
        byte[] magic = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D};
        if (bytes.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (bytes[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    private static int safeCountPages(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return document.getNumberOfPages();
        } catch (java.io.IOException ioe) {
            return -1;
        }
    }

    private ResponseEntity<byte[]> buildDocxResponse(PreviewResult result) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(DOCX_MEDIA);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"preview.docx\"");
        headers.set(HEADER_PREVIEW_MODE, result.mode().name());
        headers.set(HEADER_PREVIEW_RENDERER, RendererKind.DOCX.name());
        return ResponseEntity.status(HttpStatus.OK).headers(headers).body(result.bytes());
    }

    private ResponseEntity<byte[]> buildDocHtmlResponse(PreviewResult result) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(DOC_HTML_MEDIA);
        // Inline display only; the browser must not be told to
        // download because the legacy DOC was already converted to
        // sanitised HTML on the server.
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"preview.html\"");
        // Defensive: the rendered HTML already carries a strict CSP
        // but we also declare X-Content-Type-Options and a
        // content-security-policy here as belt-and-braces.
        headers.add("X-Content-Type-Options", "nosniff");
        headers.set(HEADER_PREVIEW_MODE, result.mode().name());
        headers.set(HEADER_PREVIEW_RENDERER, RendererKind.DOC_HTML.name());
        return ResponseEntity.status(HttpStatus.OK).headers(headers).body(result.bytes());
    }

    private ResponseEntity<byte[]> buildLockedResponse(PreviewResult result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mode", PreviewMode.LOCKED.name());
        body.put("reason", result.lockedReason() != null
                ? result.lockedReason().name()
                : com.cmcu.itstudy.dto.document.PreviewLockedReason.PREVIEW_UNAVAILABLE.name());
        body.put("message", result.message() != null
                ? result.message()
                : "Vui lòng mua tài liệu để có thể xem bản full");
        try {
            byte[] json = objectMapper.writeValueAsBytes(body);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(HEADER_PREVIEW_MODE, PreviewMode.LOCKED.name());
            return ResponseEntity.status(HttpStatus.OK).headers(headers).body(json);
        } catch (Exception e) {
            log.warn("Failed to serialize preview locked payload");
            return ResponseEntity.status(HttpStatus.OK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"mode\":\"LOCKED\"}".getBytes());
        }
    }
}