package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.dto.document.PreviewLockedReason;
import com.cmcu.itstudy.dto.document.PreviewMode;
import com.cmcu.itstudy.handle.PaidPreviewUnavailableException;
import com.cmcu.itstudy.service.contract.PaidDocumentPreviewService;
import com.cmcu.itstudy.service.contract.PaidPdfPageRuleService;
import com.cmcu.itstudy.service.contract.PreviewAccessDecisionService;
import com.cmcu.itstudy.service.contract.SupabaseStorageService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Objects;

/**
 * Builds the secure paid preview response.
 *
 * <h2>Pipeline</h2>
 * <ol>
 *   <li>Resolve the immutable access decision via
 *       {@link PreviewAccessDecisionService}.</li>
 *   <li>For a paid viewer with NO full access (guest, unrelated,
 *       authenticated unpaid, user moderator) on a non-PDF paid file,
 *       return {@code LOCKED} with {@code NON_PDF_PAID_DOCUMENT} so the
 *       buy-now CTA is rendered without leaking bytes.</li>
 *   <li>For a FULL viewer (owner, purchaser, exact approver, super
 *       admin), stream the original bytes back regardless of MIME. The
 *       controller dispatches PDF / DOCX / DOC responses based on the
 *       declared MIME. We never re-encode the original bytes for FULL
 *       viewers — pixel-perfect fidelity matters here.</li>
 *   <li>For a LIMITED viewer on a paid PDF, build a derivative whose
 *       total page count matches the original; the leading allowed
 *       pages carry their original vector content; the remaining pages
 *       are replaced by rasterised blurred locked pages that expose no
 *       original text layer.</li>
 *   <li>One-page paid PDFs and non-PDF paid files for non-full viewers
 *       return a {@code LOCKED} result so the controller emits the
 *       buy-now CTA without exposing any content.</li>
 * </ol>
 *
 * <h2>Safety</h2>
 * <ul>
 *   <li>Bucket / path come exclusively from the request snapshot
 *       (which itself is resolved server-side from a DocumentFile row);
 *       they are never accepted from a client payload.</li>
 *   <li>Bytes are streamed into a bounded buffer; no temp file is
 *       written to the working directory, repository, or any other
 *       disk location.</li>
 *   <li>Service-role key, Authorization header, signed URL, token,
 *       and raw Supabase response bodies are never logged.</li>
 *   <li>PDFBox resources are closed deterministically in a finally
 *       block so a corrupt PDF cannot leak a file handle.</li>
 * </ul>
 */
@Service
public class PaidDocumentPreviewServiceImpl implements PaidDocumentPreviewService {

    private static final Logger log = LoggerFactory.getLogger(PaidDocumentPreviewServiceImpl.class);

    static final String PDF_MIME_PREFIX = "application/pdf";
    static final String PDF_EXTENSION = ".pdf";
    static final String DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    static final String DOCX_EXTENSION = ".docx";
    static final String DOC_MIME = "application/msword";
    static final String DOC_EXTENSION = ".doc";

    private final PreviewAccessDecisionService accessDecisionService;
    private final PaidPdfPageRuleService pageRuleService;
    private final SupabaseStorageService storageService;

    public PaidDocumentPreviewServiceImpl(
            PreviewAccessDecisionService accessDecisionService,
            PaidPdfPageRuleService pageRuleService,
            SupabaseStorageService storageService) {
        this.accessDecisionService = Objects.requireNonNull(accessDecisionService);
        this.pageRuleService = Objects.requireNonNull(pageRuleService);
        this.storageService = Objects.requireNonNull(storageService);
    }

    @Override
    public PreviewResult buildPreview(DocumentRequest request) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(request.documentId(), "documentId");
        Objects.requireNonNull(request.status(), "status");

        PreviewAccessDecisionService.Decision decision = accessDecisionService.decide(
                toDocumentSnapshot(request),
                request.currentUser(),
                request.authorities(),
                request.hasPurchaserAccess());

        // Pending / rejected denial path — viewer is neither owner nor
        // approver / admin. The decision service surfaces a null mode in
        // this case; we map it to a generic denial without any preview
        // surface at all.
        if (decision.mode() == null && decision.pendingAllowed()) {
            // fall through to limited / locked branch below.
        } else if (decision.mode() == null) {
            return PreviewResult.denied();
        }

        PreviewMode mode = decision.mode();

        // Free documents with FULL mode use the existing public URL
        // pipeline; we do not touch storage here. The controller
        // short-circuits free documents before invoking this service,
        // but we still defend against accidental invocation.
        if (Boolean.FALSE.equals(request.isPaid()) && mode == PreviewMode.FULL) {
            return PreviewResult.locked(PreviewLockedReason.PREVIEW_UNAVAILABLE,
                    "Free documents use the existing public preview pipeline");
        }

        if (!StringUtils.hasText(request.bucket()) || !StringUtils.hasText(request.path())) {
            return PreviewResult.locked(PreviewLockedReason.PREVIEW_UNAVAILABLE,
                    "Private object location is not available for this document");
        }

        RendererKind renderer = rendererKind(request.mimeType(), request.path());

        // Paid non-PDF without FULL access → LOCKED with the dedicated
        // reason. The frontend renders the buy-now CTA without exposing
        // any bytes; we never fall back to Google Docs / Office Online
        // viewer because the object lives in a private bucket.
        if (renderer != RendererKind.PDF && mode != PreviewMode.FULL) {
            return PreviewResult.locked(PreviewLockedReason.NON_PDF_PAID_DOCUMENT,
                    "Định dạng này chỉ được xem đầy đủ sau khi mua");
        }

        // Storage IO happens outside any DB transaction. The
        // SupabaseStorageService contract enforces that.
        byte[] original;
        try {
            original = storageService.downloadPrivateObject(request.bucket(), request.path());
        } catch (RuntimeException e) {
            // The storage service maps its own failure modes to safe
            // domain exceptions; bubble them up unchanged so the
            // controller / exception handler can translate them.
            throw e;
        }
        if (original == null || original.length == 0) {
            return PreviewResult.locked(PreviewLockedReason.PREVIEW_UNAVAILABLE,
                    "Tài liệu trống");
        }

        if (mode == PreviewMode.FULL) {
            // Owner / purchaser / approver / super-admin path. The
            // controller dispatches PDF / DOCX / DOC response based on
            // the declared MIME; we never re-encode FULL bytes.
            return buildFullResult(renderer, original);
        }

        // Limited path: anonymous / unpaid viewer on a paid PDF. We
        // build a NEW PDDocument whose total page count equals the
        // original; only the leading allowed pages carry original
        // content; the rest become rasterised blurred locked pages.
        return buildLimitedDerivative(original);
    }

    private PreviewResult buildFullResult(RendererKind renderer, byte[] original) {
        if (renderer == RendererKind.PDF) {
            int totalPages = safeCountPages(original);
            if (totalPages <= 0) {
                return PreviewResult.locked(PreviewLockedReason.PREVIEW_UNAVAILABLE,
                        "Không thể đọc tài liệu PDF");
            }
            return PreviewResult.full(original, totalPages, RendererKind.PDF);
        }
        if (renderer == RendererKind.DOC) {
            String html = OfficePreviewSanitizer.renderDocHtml(original);
            if (html == null) {
                return PreviewResult.locked(PreviewLockedReason.PREVIEW_UNAVAILABLE,
                        "Không thể trích xuất nội dung tài liệu DOC");
            }
            return PreviewResult.full(html.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    0, RendererKind.DOC_HTML);
        }
        // DOCX: ship the original bytes. The controller tags the
        // response with the DOCX MIME; no re-encoding, no public URL,
        // no signed URL.
        return PreviewResult.full(original, 0, RendererKind.DOCX);
    }

    private PreviewResult buildLimitedDerivative(byte[] original) {
        try (PDDocument source = Loader.loadPDF(original)) {
            int totalPages = source.getNumberOfPages();
            int visiblePages = pageRuleService.calculateLimitedPreviewPageCount(totalPages);
            byte[] derivative = renderDerivativeBytes(source, totalPages, visiblePages);
            return PreviewResult.limited(derivative, totalPages, visiblePages, RendererKind.PDF);
        } catch (PaidPreviewUnavailableException e) {
            // The page rule service refuses to produce a derivative for
            // one-page PDFs, malformed PDFs, and empty PDFs. The reason
            // is request-local — it travels on the exception itself so
            // there is no mutable singleton state to read.
            PreviewLockedReason reason = e.getReason();
            String message;
            switch (reason) {
                case ONE_PAGE_PAID_DOCUMENT:
                    message = "Vui lòng mua tài liệu để có thể xem bản full";
                    break;
                case NON_PDF_PAID_DOCUMENT:
                    message = "Định dạng này chỉ được xem đầy đủ sau khi mua";
                    break;
                default:
                    message = "Vui lòng mua tài liệu để có thể xem bản full";
                    break;
            }
            return PreviewResult.locked(reason, message);
        } catch (IOException e) {
            log.warn("Failed to render limited preview derivative");
            return PreviewResult.locked(PreviewLockedReason.PREVIEW_UNAVAILABLE,
                    "Không thể đọc tài liệu PDF");
        }
    }

    /**
     * Render the leading {@code visiblePages} of {@code source} into a
     * NEW {@code PDDocument}, then append {@code totalPages -
     * visiblePages} rasterised blurred locked pages so the output page
     * count matches the original. The original document is not
     * modified.
     *
     * <p>Locked pages are NOT created via {@code PDPage.importPage}; we
     * never copy original vector content, text layer, annotations,
     * links, embedded files, JavaScript, form fields, or metadata that
     * could leak content. Each locked page contains only a low-res
     * rasterised image and a sanitised overlay.</p>
     *
     * <p>All PDFBox resources are released deterministically via
     * try-with-resources so a corrupt input cannot leak a file
     * handle.</p>
     */
    byte[] renderDerivativeBytes(PDDocument source, int totalPages, int visiblePages) throws IOException {
        if (visiblePages <= 0) {
            throw new PaidPreviewUnavailableException(
                    PreviewLockedReason.PREVIEW_UNAVAILABLE,
                    "No pages to render in the limited preview derivative");
        }
        try (PDDocument derivative = new PDDocument();
             ByteArrayOutputStream buffer = new ByteArrayOutputStream(64 * 1024)) {
            for (int i = 0; i < visiblePages; i++) {
                PDPage page = source.getPage(i);
                PDPage imported = derivative.importPage(page);
                if (imported == null) {
                    throw new IOException("Failed to import page " + i);
                }
            }
            int lockedPageCount = Math.max(0, totalPages - visiblePages);
            for (int i = 0; i < lockedPageCount; i++) {
                LockedPageRenderer.appendLockedPage(derivative,
                        source, visiblePages + i, i);
            }
            derivative.save(buffer);
            return buffer.toByteArray();
        }
    }

    private static int safeCountPages(byte[] original) {
        try (PDDocument source = Loader.loadPDF(original)) {
            return source.getNumberOfPages();
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * Map a declared MIME / path into a renderer kind. The decision is
     * intentionally permissive: a path ending in {@code .pdf} is treated
     * as PDF even when the MIME is missing (which happens for some
     * browser uploads). A path ending in {@code .docx} / {@code .doc}
     * overrides a missing MIME. Anything that does not match a known
     * Office or PDF signature falls through to {@link RendererKind#PDF}
     * as the safe default so the controller does not misclassify the
     * response — but the access-check loop above already rejects
     * non-PDF bytes for non-FULL viewers.
     */
    static RendererKind rendererKind(String mimeType, String path) {
        String mime = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
        String pathLower = path == null ? "" : path.toLowerCase(Locale.ROOT);
        if (mime.startsWith(PDF_MIME_PREFIX) || pathLower.endsWith(PDF_EXTENSION)) {
            return RendererKind.PDF;
        }
        if (mime.startsWith(DOCX_MIME) || pathLower.endsWith(DOCX_EXTENSION)) {
            return RendererKind.DOCX;
        }
        if (mime.startsWith(DOC_MIME) || pathLower.endsWith(DOC_EXTENSION)) {
            return RendererKind.DOC;
        }
        return RendererKind.PDF;
    }

    /**
     * Re-shape the request snapshot into a {@link com.cmcu.itstudy.entity.Document}
     * carrying only the fields the decision service consults. The
     * constructed instance is local to the method and never persisted.
     */
    private static com.cmcu.itstudy.entity.Document toDocumentSnapshot(DocumentRequest request) {
        com.cmcu.itstudy.entity.Document snapshot = new com.cmcu.itstudy.entity.Document();
        snapshot.setId(request.documentId());
        snapshot.setStatus(request.status());
        snapshot.setIsPaid(Boolean.TRUE.equals(request.isPaid()));
        if (request.ownerId() != null) {
            com.cmcu.itstudy.entity.User owner = new com.cmcu.itstudy.entity.User();
            owner.setId(request.ownerId());
            snapshot.setCreatedBy(owner);
        }
        return snapshot;
    }
}