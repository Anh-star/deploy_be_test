package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.config.SupabaseProperties;
import com.cmcu.itstudy.dto.office.OfficeConversionRequest;
import com.cmcu.itstudy.dto.office.OfficeConversionResult;
import com.cmcu.itstudy.entity.DocumentFile;
import com.cmcu.itstudy.enums.AllowedDocumentFileType;
import com.cmcu.itstudy.enums.DocumentPreviewArtifactKind;
import com.cmcu.itstudy.enums.DocumentPreviewArtifactStatus;
import com.cmcu.itstudy.enums.StorageCleanupReason;
import com.cmcu.itstudy.handle.OfficeConversionInterruptedException;
import com.cmcu.itstudy.repository.DocumentFileRepository;
import com.cmcu.itstudy.repository.DocumentPreviewArtifactRepository;
import com.cmcu.itstudy.repository.custom.DocumentPreviewArtifactClaim;
import com.cmcu.itstudy.repository.custom.SafeArtifactLastError;
import com.cmcu.itstudy.service.contract.DocumentPreviewArtifactClaimService;
import com.cmcu.itstudy.service.contract.DocumentPreviewArtifactReadySignal;
import com.cmcu.itstudy.service.contract.DocumentPreviewServerUploadService;
import com.cmcu.itstudy.service.contract.OfficeDocumentConverter;
import com.cmcu.itstudy.service.contract.OfficePdfValidationService;
import com.cmcu.itstudy.service.contract.PaidPdfPageRuleService;
import com.cmcu.itstudy.service.contract.QuizGenerationService;
import com.cmcu.itstudy.service.contract.StorageCleanupTaskService;
import com.cmcu.itstudy.service.contract.SupabaseStorageService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Per-artifact processing service for the Phase&nbsp;O3 preview worker.
 *
 * <p>This service is the single place that turns an immutable
 * {@link DocumentPreviewArtifactClaim} into a guarded
 * {@code READY} / {@code RETRY} / {@code DEAD} transition. Every
 * state change goes through the existing
 * {@link DocumentPreviewArtifactClaimRepository} fragment, which runs
 * each transition in its own short {@code REQUIRES_NEW} transaction.</p>
 *
 * <h2>Consistent {@code now} contract</h2>
 * <p>The caller (the worker scheduler) supplies a single
 * {@code now} value computed once at the start of the cycle. The
 * processor reuses that same {@code now} for:</p>
 * <ul>
 *   <li>the {@code markReady} timestamp;</li>
 *   <li>the {@code markRetry} timestamp;</li>
 *   <li>the {@code markDead} timestamp;</li>
 *   <li>the {@link DocumentPreviewBackoffCalculator#nextAttemptAt(LocalDateTime, int)}
 *       call (retry scheduling).</li>
 * </ul>
 * <p>The processor does NOT independently read the {@link Clock} once
 * the cycle-supplied {@code now} has been delivered. This guarantees
 * the database timestamps, the {@code staleBefore} cutoff and the
 * scheduled {@code nextAttemptAt} all agree on the same instant.</p>
 *
 * <h2>Interruption checkpoints</h2>
 * <p>The processor calls {@link #ensureNotInterrupted(String)} at every
 * documented boundary in both the FULL and LIMITED flows. Each
 * checkpoint:</p>
 * <ul>
 *   <li>inspects {@link Thread#currentThread()#isInterrupted()} without
 *       clearing the flag;</li>
 *   <li>throws {@link InterruptionSignal} (an internal unchecked
 *       signal) when the flag is set;</li>
 *   <li>is caught at the top of {@link #process} and translated to
 *       {@link WorkerOutcome#INTERRUPTED} WITHOUT invoking
 *       {@code markRetry}, {@code markDead} or {@code markReady};</li>
 *   <li>when triggered after an upload has succeeded, the post-upload
 *       hook enqueues a cleanup task for the exact attempt-owned path
 *       the worker just wrote, then propagates {@code INTERRUPTED};</li>
 *   <li>when triggered before the upload, no cleanup is enqueued (no
 *       object was written).</li>
 * </ul>
 *
 * <h2>Deterministic, attempt-owned paths</h2>
 * <p>The processor delegates path construction to
 * {@link DocumentPreviewPathBuilder} with the full
 * {@code (documentFileId, artifactId, kind, variantVersion,
 * claimedAttemptCount)} tuple. There is no random suffix, no UUID
 * suffix, and no user-supplied fragment.</p>
 *
 * <h2>Transaction boundaries</h2>
 * <ul>
 *   <li>The outer {@link #process(DocumentPreviewArtifactClaim, LocalDateTime)} method
 *       is annotated {@code NOT_SUPPORTED} so no database transaction
 *       is open while the worker downloads the source, runs the
 *       converter, validates the PDF, and uploads the preview.</li>
 *   <li>Every {@code markReady}/{@code markRetry}/{@code markDead}
 *       call runs in its own {@code REQUIRES_NEW} transaction via the
 *       repository fragment.</li>
 *   <li>If a guarded state update returns {@code false} (lost
 *       ownership), the worker reports {@code LOST_OWNERSHIP} and
 *       does not attempt a second state overwrite.</li>
 * </ul>
 */
@Service
public class DocumentPreviewArtifactProcessor {

    private static final Logger log =
            LoggerFactory.getLogger(
                    DocumentPreviewArtifactProcessor.class);

    private final DocumentPreviewArtifactRepository claimRepository;
    private final DocumentPreviewArtifactRepository artifactRepository;
    private final DocumentFileRepository documentFileRepository;
    private final SupabaseStorageService supabaseStorageService;
    private final OfficeDocumentConverter officeDocumentConverter;
    private final OfficePdfValidationService officePdfValidationService;
    private final DocumentPreviewServerUploadService previewServerUploadService;
    private final DocumentPreviewPathBuilder pathBuilder;
    private final DocumentPreviewBackoffCalculator backoffCalculator;
    private final DocumentPreviewFailureClassifier failureClassifier;
    private final StorageCleanupTaskService cleanupTaskService;
    private final PaidDocumentPreviewServiceImpl paidPreviewService;
    private final PaidPdfPageRuleService pageRuleService;
    private final SupabaseProperties supabaseProperties;
    private final Clock clock;
    private final DocumentPreviewArtifactReadySignal readySignal;
    private final QuizGenerationService quizGenerationService;

    @Autowired
    public DocumentPreviewArtifactProcessor(
            @Qualifier("documentPreviewArtifactRepository")
            DocumentPreviewArtifactRepository claimRepository,
            @Qualifier("documentPreviewArtifactRepository")
            DocumentPreviewArtifactRepository artifactRepository,
            DocumentFileRepository documentFileRepository,
            SupabaseStorageService supabaseStorageService,
            OfficeDocumentConverter officeDocumentConverter,
            OfficePdfValidationService officePdfValidationService,
            DocumentPreviewServerUploadService previewServerUploadService,
            DocumentPreviewPathBuilder pathBuilder,
            DocumentPreviewBackoffCalculator backoffCalculator,
            DocumentPreviewFailureClassifier failureClassifier,
            StorageCleanupTaskService cleanupTaskService,
            PaidDocumentPreviewServiceImpl paidPreviewService,
            PaidPdfPageRuleService pageRuleService,
            SupabaseProperties supabaseProperties,
            Clock clock,
            DocumentPreviewArtifactReadySignal readySignal,
            QuizGenerationService quizGenerationService) {
        this.claimRepository = Objects.requireNonNull(claimRepository,
                "claimRepository");
        this.artifactRepository = Objects.requireNonNull(artifactRepository,
                "artifactRepository");
        this.documentFileRepository = Objects.requireNonNull(
                documentFileRepository, "documentFileRepository");
        this.supabaseStorageService = Objects.requireNonNull(
                supabaseStorageService, "supabaseStorageService");
        this.officeDocumentConverter = Objects.requireNonNull(
                officeDocumentConverter, "officeDocumentConverter");
        this.officePdfValidationService = Objects.requireNonNull(
                officePdfValidationService, "officePdfValidationService");
        this.previewServerUploadService = Objects.requireNonNull(
                previewServerUploadService, "previewServerUploadService");
        this.pathBuilder = Objects.requireNonNull(pathBuilder, "pathBuilder");
        this.backoffCalculator = Objects.requireNonNull(backoffCalculator,
                "backoffCalculator");
        this.failureClassifier = Objects.requireNonNull(failureClassifier,
                "failureClassifier");
        this.cleanupTaskService = Objects.requireNonNull(cleanupTaskService,
                "cleanupTaskService");
        this.paidPreviewService = Objects.requireNonNull(paidPreviewService,
                "paidPreviewService");
        this.pageRuleService = Objects.requireNonNull(pageRuleService,
                "pageRuleService");
        this.supabaseProperties = Objects.requireNonNull(supabaseProperties,
                "supabaseProperties");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.readySignal = Objects.requireNonNull(readySignal, "readySignal");
        this.quizGenerationService = Objects.requireNonNull(
                quizGenerationService, "quizGenerationService");
    }

    /**
     * Process a single claimed artifact using the cycle-supplied
     * {@code now} value. The method is intentionally declared
     * {@code NOT_SUPPORTED} so no database transaction is open while
     * the worker downloads the source, runs the converter, validates
     * the PDF, and uploads the preview.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public WorkerOutcome process(DocumentPreviewArtifactClaim claim,
                                 LocalDateTime now) {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(now, "now");
        // Phase-1 speed timing anchor. Wall-clock millis since the
        // processor entered this method. Used ONLY for the final
        // summary log — never for any decision. We deliberately
        // use System.nanoTime() so a wall-clock adjustment cannot
        // produce negative or duplicate timings.
        final long t0Nanos = System.nanoTime();
        log.info("Processing artifactId={} kind={} attemptCount={}/{}",
                claim.artifactId(), claim.artifactKind(),
                claim.attemptCount(), claim.maxAttempts());

        try {
            WorkerOutcome outcome = null;
            try {
                if (claim.artifactKind() == DocumentPreviewArtifactKind.FULL) {
                    outcome = processFull(claim, now);
                } else {
                    outcome = processLimited(claim, now);
                }
            } finally {
                // Phase-1 speed: emit the total-processing timing on
                // EVERY exit path so the operator can measure how long
                // the worker spent on this artifact. We deliberately
                // DO NOT log bucket/path (sensitive), Supabase URL,
                // Authorization, service-role key, signed URL, or
                // document bytes. The four recorded fields are
                // artifactId, attemptCount, totalProcessingMs,
                // finalStatus.
                long totalMs = (System.nanoTime() - t0Nanos) / 1_000_000L;
                String finalStatus = (outcome == null)
                        ? "EXCEPTION" : outcome.name();
                log.info("Artifact processed artifactId={} attemptCount={} "
                                + "totalProcessingMs={} finalStatus={}",
                        claim.artifactId(), claim.attemptCount(),
                        totalMs, finalStatus);
            }
            return outcome;
        } catch (OfficeConversionInterruptedException interrupted) {
            // The O1 conversion pipeline raised a typed interruption.
            // Preserve the interrupt flag but DO NOT clear it here:
            // the worker scheduler checks the flag and decides whether
            // to stop the batch. The processing of this artifact is
            // abandoned; the row will be reclaimed by a future cycle
            // through the stale-PENDING reclaim path.
            Thread.currentThread().interrupt();
            log.warn("Artifact processing interrupted id={}",
                    claim.artifactId());
            return WorkerOutcome.INTERRUPTED;
        } catch (InterruptionSignal signal) {
            // Internal checkpoint trip. No state transition was applied
            // to the artifact row. If a PDF upload already succeeded
            // for this attempt, schedule cleanup of the exact
            // attempt-owned path so the orphaned object is removed.
            Thread.currentThread().interrupt();
            if (signal.uploadedBucket() != null
                    && signal.uploadedPath() != null) {
                log.warn("Interruption after upload id={}; scheduling cleanup",
                        claim.artifactId());
                return compensateOrphanedUpload(claim,
                        signal.uploadedBucket(), signal.uploadedPath(), now,
                        WorkerOutcome.INTERRUPTED);
            }
            log.warn("Artifact processing interrupted at {} id={}",
                    signal.stage(), claim.artifactId());
            return WorkerOutcome.INTERRUPTED;
        } catch (RuntimeException e) {
            DocumentPreviewFailureClassifier.Decision decision =
                    failureClassifier.classify(e,
                            claim.attemptCount(), claim.maxAttempts());
            String code = failureClassifier.safeOperationalCode(e);
            log.warn("Artifact processing failed id={} code={} decision={}",
                    claim.artifactId(), code, decision);
            return applyDecision(claim, decision, code, now);
        }
    }

    private WorkerOutcome processFull(DocumentPreviewArtifactClaim claim,
                                       LocalDateTime now) {
        // Phase-4 timing anchor (download). Wall-clock millis
        // since the start of source download. Used ONLY for the
        // stage breakdown log under
        // totalProcessingMs. We deliberately use
        // System.nanoTime() so a wall-clock adjustment cannot
        // produce negative or duplicate timings.
        long tDownloadStartNanos = 0L;
        long tDownloadEndNanos = 0L;
        long tConvertEndNanos = 0L;
        long tUploadStartNanos = 0L;
        long tUploadEndNanos = 0L;
        // Checkpoint #1: before source download.
        ensureNotInterrupted("full.before-download", null, null);

        // 1. Look up the source DocumentFile.
        DocumentFile source = documentFileRepository.findById(
                claim.documentFileId()).orElse(null);
        if (source == null) {
            log.warn("Source DocumentFile missing for preview id={}",
                    claim.artifactId());
            return applyDecision(claim,
                    DocumentPreviewFailureClassifier.Decision.PERMANENT_DEAD,
                    "O3_SOURCE_MISSING", now);
        }

        // 2. Validate all authoritative source metadata before download.
        //    The record carries both the resolved file type and any error
        //    code so the caller can produce the correct guarded decision.
        OfficeSourceValidation validation = OfficeSourceValidation.from(source);
        if (validation.fileType() == null) {
            log.warn("Source metadata validation failed for preview id={} reason={}",
                    claim.artifactId(), validation.errorCode());
            return applyDecision(claim,
                    DocumentPreviewFailureClassifier.Decision.PERMANENT_DEAD,
                    validation.errorCode(), now);
        }
        AllowedDocumentFileType fileType = validation.fileType();

        // 3. Download the original Office bytes.
        byte[] originalBytes;
        try {
            tDownloadStartNanos = System.nanoTime();
            originalBytes = supabaseStorageService.downloadPrivateObject(
                    source.getStorageBucket(), source.getStoragePath());
            tDownloadEndNanos = System.nanoTime();
        } catch (RuntimeException e) {
            logStageTimings(claim,
                    tDownloadStartNanos, tDownloadEndNanos,
                    tConvertEndNanos, tUploadStartNanos, tUploadEndNanos);
            return applyDecision(claim,
                    failureClassifier.classify(e, claim.attemptCount(),
                            claim.maxAttempts()),
                    failureClassifier.safeOperationalCode(e), now);
        }
        if (originalBytes == null || originalBytes.length == 0) {
            logStageTimings(claim,
                    tDownloadStartNanos, tDownloadEndNanos,
                    tConvertEndNanos, tUploadStartNanos, tUploadEndNanos);
            return applyDecision(claim,
                    DocumentPreviewFailureClassifier.Decision.PERMANENT_DEAD,
                    "O3_SOURCE_MISSING", now);
        }

        // Checkpoint #2: after source download.
        ensureNotInterrupted("full.after-download", null, null);

        // Checkpoint #3: before conversion.
        ensureNotInterrupted("full.before-conversion", null, null);

        // 4. Convert.
        OfficeConversionRequest request = new OfficeConversionRequest(
                originalBytes, fileType, claim.artifactId().toString());
        OfficeConversionResult result;
        try {
            result = officeDocumentConverter.convert(request);
            tConvertEndNanos = System.nanoTime();
        } catch (RuntimeException e) {
            logStageTimings(claim,
                    tDownloadStartNanos, tDownloadEndNanos,
                    tConvertEndNanos, tUploadStartNanos, tUploadEndNanos);
            return applyDecision(claim,
                    failureClassifier.classify(e, claim.attemptCount(),
                            claim.maxAttempts()),
                    failureClassifier.safeOperationalCode(e), now);
        }

        // Checkpoint #4: immediately after conversion, before
        // validation. A failure here leaves no object on Supabase; the
        // worker simply reports INTERRUPTED.
        ensureNotInterrupted("full.after-conversion", null, null);

        // 5. Validate the generated PDF on disk through the frozen
        //    validator. The validator already counts pages, so we
        //    reuse the count for the guarded markReady.
        int pageCount;
        Path tempPdf = null;
        try {
            tempPdf = Files.createTempFile("preview-full-", ".pdf");
            Files.write(tempPdf, result.pdfBytes());
            pageCount = officePdfValidationService.validateAndCountPages(
                    tempPdf);
        } catch (IOException e) {
            return applyDecision(claim,
                    DocumentPreviewFailureClassifier.Decision.RETRYABLE,
                    "O3_VALIDATION_IO", now);
        } catch (RuntimeException e) {
            return applyDecision(claim,
                    failureClassifier.classify(e, claim.attemptCount(),
                            claim.maxAttempts()),
                    failureClassifier.safeOperationalCode(e), now);
        } finally {
            if (tempPdf != null) {
                try {
                    Files.deleteIfExists(tempPdf);
                } catch (IOException ignored) {
                    // The temp file lives in the JVM default temp dir;
                    // the OS reclaims it on next boot.
                }
            }
        }
        if (pageCount <= 0) {
            return applyDecision(claim,
                    DocumentPreviewFailureClassifier.Decision.PERMANENT_DEAD,
                    "O3_INVALID_OUTPUT", now);
        }

        // Checkpoint #5: after PDF validation.
        ensureNotInterrupted("full.after-validation", null, null);

        // 6. Build the deterministic, attempt-owned preview path.
        String previewBucket = supabaseProperties.resolvedPrivatePreviewBucket();
        if (previewBucket == null || previewBucket.isBlank()) {
            return applyDecision(claim,
                    DocumentPreviewFailureClassifier.Decision.PERMANENT_DEAD,
                    "O3_BUCKET_NOT_CONFIGURED", now);
        }
        String previewPath = pathBuilder.buildFullPreviewPath(
                claim.documentFileId(),
                claim.artifactId(),
                claim.artifactKind(),
                claim.variantVersion(),
                claim.attemptCount());

        // Checkpoint #6: immediately before upload. We pass null
        // bucket/path here so the outer catch recognises this as a
        // pre-upload interruption; no cleanup task is enqueued
        // because no object was written to Supabase.
        ensureNotInterrupted("full.before-upload", null, null);

        // 7. Upload the PDF.
        try {
            tUploadStartNanos = System.nanoTime();
            previewServerUploadService.uploadPdfPreview(
                    previewBucket, previewPath, result.pdfBytes(),
                    "application/pdf");
            tUploadEndNanos = System.nanoTime();
        } catch (RuntimeException e) {
            logStageTimings(claim,
                    tDownloadStartNanos, tDownloadEndNanos,
                    tConvertEndNanos, tUploadStartNanos, tUploadEndNanos);
            return applyDecision(claim,
                    failureClassifier.classify(e, claim.attemptCount(),
                            claim.maxAttempts()),
                    failureClassifier.safeOperationalCode(e), now);
        }

        // Checkpoint #7: immediately after upload, before markReady.
        // The uploaded object belongs to this EXACT attempt and MUST
        // be cleaned up on interruption; the InterruptionSignal
        // carries the attempt-owned coordinates so the outer catch
        // can enqueue cleanup.
        ensureNotInterrupted("full.after-upload", previewBucket, previewPath);

        // Phase 2C: bridge coordinates are captured here so they are
        // available after markReady returns. The actual bridge call fires
        // AFTER markReady's REQUIRES_NEW transaction has committed.
        //
        // Phase 2C E2E wiring fix: source is a DocumentFile, so
        // source.getId() returns the DocumentFile's id (a primary key
        // of the tbl_document_files row), NOT the parent Document's id.
        // The bridge MUST receive the parent Document's id, so we
        // resolve it via a tiny dedicated projection query that
        // returns just the parent Document id as a UUID. This avoids
        // touching the LAZY DocumentFile.document association from
        // a NOT_SUPPORTED transactional context (which would throw
        // LazyInitializationException).
        //
        // The resolution is wrapped in a defensive Optional chain: a
        // missing DocumentFile row or a broken document_id FK must
        // NEVER crash the preview pipeline. The bridge is best-effort;
        // if the resolution fails we simply skip it (the natural
        // worker poll will retry on a future cycle).
        final UUID docFileId = claim.documentFileId();
        final UUID documentId =
                documentFileRepository.findDocumentIdByDocumentFileId(
                                docFileId)
                        .orElse(null);
        if (documentId == null) {
            log.warn("Phase 2C: cannot resolve parent Document.id for "
                    + "DocumentFile.id={}; skipping the source-ready bridge.",
                    docFileId);
        }
        final LocalDateTime ts = now;

        // 8. Guarded markReady — runs in its own REQUIRES_NEW transaction.
        boolean ready = claimRepository.markReady(
                claim.artifactId(), claim.attemptCount(),
                previewBucket, previewPath, pageCount, now);
        if (ready) {
            logStageTimings(claim,
                    tDownloadStartNanos, tDownloadEndNanos,
                    tConvertEndNanos, tUploadStartNanos, tUploadEndNanos);
            log.info("Artifact READY id={} pages={} bucket={} path={}",
                    claim.artifactId(), pageCount,
                    previewBucket, previewPath);
            // Wake-up: the dependent LIMITED row for this
            // (documentFileId, checksum, variantVersion) is now
            // claimable because its FULL sibling is READY. The wake-up
            // is best-effort; the natural 3-second poll will pick it
            // up if the signal is dropped.
            try {
                readySignal.fire();
            } catch (RuntimeException ignored) {
                // Wake-up is best-effort.
            }

            // Phase 2C: execute the source-ready bridge. Direct call — no
            // @Transactional on queueWhenSourceReady. The call is best-effort:
            // if it throws, the generation stays in WAITING_SOURCE and the
            // natural worker poll will eventually pick it up on a future cycle.
            // The bridge is skipped entirely when documentId could not be
            // resolved (broken FK / missing DocumentFile row).
            if (documentId != null) {
                try {
                    quizGenerationService.queueWhenSourceReady(documentId, docFileId, ts);
                } catch (RuntimeException e) {
                    log.warn(
                            "Source-ready bridge failed for documentId={} docFileId={}",
                            documentId, docFileId, e);
                }
            }

            return WorkerOutcome.READY;
        }
        log.warn("Artifact markReady lost ownership id={}; scheduling cleanup",
                claim.artifactId());
        return compensateOrphanedUpload(claim, previewBucket, previewPath, now,
                WorkerOutcome.LOST_OWNERSHIP);
    }

    private WorkerOutcome processLimited(DocumentPreviewArtifactClaim claim,
                                          LocalDateTime now) {
        // Checkpoint #1: before FULL artifact download.
        ensureNotInterrupted("limited.before-full-download", null, null);

        // 1. Find the corresponding READY FULL artifact.
        var fullArtifact = artifactRepository
                .findFirstByDocumentFileIdAndArtifactKindAndSourceChecksumSha256AndStatusAndVariantVersion(
                        claim.documentFileId(),
                        DocumentPreviewArtifactKind.FULL,
                        claim.sourceChecksumSha256(),
                        DocumentPreviewArtifactStatus.READY,
                        claim.variantVersion());
        if (fullArtifact.isEmpty() && claim.sourceChecksumSha256() == null) {
            fullArtifact = artifactRepository
                    .findFirstByDocumentFileIdAndArtifactKindAndSourceChecksumSha256AndStatusAndVariantVersion(
                            claim.documentFileId(),
                            DocumentPreviewArtifactKind.FULL,
                            null,
                            DocumentPreviewArtifactStatus.READY,
                            claim.variantVersion());
        }
        if (fullArtifact.isEmpty()) {
            // Context-specific retryability: the dependent FULL
            // preview is missing. This is retryable (FULL may be
            // regenerated or the cleanup queue may still be draining)
            // UNLESS the retry budget has been exhausted, in which
            // case the worker must transition to DEAD instead of
            // leaving the row stranded in PROCESSING.
            boolean hasBudget = claim.attemptCount() < claim.maxAttempts();
            if (!hasBudget) {
                log.warn("FULL not READY and budget exhausted for LIMITED id={}",
                        claim.artifactId());
                return applyDecision(claim,
                        DocumentPreviewFailureClassifier.Decision
                                .BUDGET_EXHAUSTED_RETRYABLE,
                        "O3_FULL_NOT_READY", now);
            }
            log.info("FULL not yet READY for LIMITED id={}; scheduling retry",
                    claim.artifactId());
            return applyDecision(claim,
                    DocumentPreviewFailureClassifier.Decision.RETRYABLE,
                    "O3_FULL_NOT_READY", now);
        }
        com.cmcu.itstudy.entity.DocumentPreviewArtifact full = fullArtifact.get();
        if (full.getStorageBucket() == null || full.getStoragePath() == null) {
            return applyDecision(claim,
                    DocumentPreviewFailureClassifier.Decision.PERMANENT_DEAD,
                    "O3_FULL_STORAGE_MISSING", now);
        }

        // 2. Download the FULL PDF bytes.
        byte[] fullBytes;
        try {
            fullBytes = supabaseStorageService.downloadPrivateObject(
                    full.getStorageBucket(), full.getStoragePath());
        } catch (RuntimeException e) {
            // Context-specific decision: a missing FULL object during
            // LIMITED processing is retryable until the budget is
            // exhausted (FULL may be regenerated, cleanup queue may
            // still be draining). A permanent-fail classification here
            // would silently strand LIMITED rows in PROCESSING.
            DocumentPreviewFailureClassifier.Decision decision =
                    failureClassifier.classifyLimitedDependencyFailure(
                            claim.attemptCount(), claim.maxAttempts());
            String code =
                    failureClassifier.safeOperationalCodeForLimited(
                            "O3_FULL_BYTES_UNAVAILABLE");
            log.warn("Limited dep download failed id={} code={} decision={}",
                    claim.artifactId(), code, decision);
            return applyDecision(claim, decision, code, now);
        }
        if (fullBytes == null || fullBytes.length == 0) {
            boolean hasBudget = claim.attemptCount() < claim.maxAttempts();
            DocumentPreviewFailureClassifier.Decision decision = hasBudget
                    ? DocumentPreviewFailureClassifier.Decision.RETRYABLE
                    : DocumentPreviewFailureClassifier.Decision
                            .BUDGET_EXHAUSTED_RETRYABLE;
            return applyDecision(claim, decision,
                    "O3_FULL_BYTES_MISSING", now);
        }

        // Checkpoint #2: after FULL artifact download.
        ensureNotInterrupted("limited.after-full-download", null, null);

        // Checkpoint #3: before LIMITED rendering.
        ensureNotInterrupted("limited.before-render", null, null);

        // 3. Render the LIMITED derivative through the existing
        //    PaidDocumentPreviewServiceImpl renderer.
        byte[] limitedBytes;
        try (PDDocument source = Loader.loadPDF(fullBytes)) {
            int totalPages = source.getNumberOfPages();
            int visiblePages = pageRuleService
                    .calculateLimitedPreviewPageCount(totalPages);
            limitedBytes = paidPreviewService.renderDerivativeBytes(
                    source, totalPages, visiblePages);
        } catch (RuntimeException e) {
            return applyDecision(claim,
                    failureClassifier.classify(e, claim.attemptCount(),
                            claim.maxAttempts()),
                    failureClassifier.safeOperationalCode(e), now);
        } catch (IOException e) {
            return applyDecision(claim,
                    DocumentPreviewFailureClassifier.Decision.RETRYABLE,
                    "O3_LIMITED_RENDER_IO", now);
        }

        // Checkpoint #4: after LIMITED rendering.
        ensureNotInterrupted("limited.after-render", null, null);

        // 4. Validate the LIMITED PDF on disk.
        int totalPages;
        Path tempPdf = null;
        try {
            tempPdf = Files.createTempFile("preview-limited-", ".pdf");
            Files.write(tempPdf, limitedBytes);
            totalPages = officePdfValidationService.validateAndCountPages(
                    tempPdf);
        } catch (IOException e) {
            return applyDecision(claim,
                    DocumentPreviewFailureClassifier.Decision.RETRYABLE,
                    "O3_VALIDATION_IO", now);
        } catch (RuntimeException e) {
            return applyDecision(claim,
                    failureClassifier.classify(e, claim.attemptCount(),
                            claim.maxAttempts()),
                    failureClassifier.safeOperationalCode(e), now);
        } finally {
            if (tempPdf != null) {
                try {
                    Files.deleteIfExists(tempPdf);
                } catch (IOException ignored) {
                    // OS reclaims on next boot.
                }
            }
        }
        if (totalPages <= 0) {
            return applyDecision(claim,
                    DocumentPreviewFailureClassifier.Decision.PERMANENT_DEAD,
                    "O3_INVALID_OUTPUT", now);
        }

        // Checkpoint #5: after validation.
        ensureNotInterrupted("limited.after-validation", null, null);

        // 5. Upload the LIMITED PDF.
        String previewBucket = supabaseProperties.resolvedPrivatePreviewBucket();
        if (previewBucket == null || previewBucket.isBlank()) {
            return applyDecision(claim,
                    DocumentPreviewFailureClassifier.Decision.PERMANENT_DEAD,
                    "O3_BUCKET_NOT_CONFIGURED", now);
        }
        String previewPath = pathBuilder.buildLimitedPreviewPath(
                claim.documentFileId(),
                claim.artifactId(),
                claim.artifactKind(),
                claim.variantVersion(),
                claim.attemptCount());

        // Checkpoint #6: immediately before upload. Pre-upload
        // interruption: no cleanup needed because no object has been
        // written yet.
        ensureNotInterrupted("limited.before-upload", null, null);

        try {
            previewServerUploadService.uploadPdfPreview(
                    previewBucket, previewPath, limitedBytes,
                    "application/pdf");
        } catch (RuntimeException e) {
            return applyDecision(claim,
                    failureClassifier.classify(e, claim.attemptCount(),
                            claim.maxAttempts()),
                    failureClassifier.safeOperationalCode(e), now);
        }

        // Checkpoint #7: immediately after upload, before markReady.
        // Post-upload interruption: the upload just succeeded and
        // belongs to this EXACT attempt; the InterruptionSignal
        // carries the attempt-owned coordinates so the outer catch
        // can enqueue cleanup.
        ensureNotInterrupted("limited.after-upload", previewBucket, previewPath);

        // 6. Guarded markReady.
        boolean ready = claimRepository.markReady(
                claim.artifactId(), claim.attemptCount(),
                previewBucket, previewPath, totalPages, now);
        if (ready) {
            log.info("Artifact READY (LIMITED) id={} pages={}",
                    claim.artifactId(), totalPages);
            return WorkerOutcome.READY;
        }
        log.warn("Artifact markReady lost ownership (LIMITED) id={}",
                claim.artifactId());
        return compensateOrphanedUpload(claim, previewBucket, previewPath, now,
                WorkerOutcome.LOST_OWNERSHIP);
    }

    private WorkerOutcome applyDecision(
            DocumentPreviewArtifactClaim claim,
            DocumentPreviewFailureClassifier.Decision decision,
            String opsCode,
            LocalDateTime now) {
        String safeError = SafeArtifactLastError.sanitize(opsCode,
                SafeArtifactLastError.OPERATIONAL_MAX_LENGTH);
        switch (decision) {
            case PERMANENT_DEAD:
            case BUDGET_EXHAUSTED_RETRYABLE:
                boolean dead = claimRepository.markDead(
                        claim.artifactId(), claim.attemptCount(),
                        safeError, now);
                return dead ? WorkerOutcome.DEAD : WorkerOutcome.LOST_OWNERSHIP;
            case RETRYABLE:
                LocalDateTime nextAttempt = backoffCalculator.nextAttemptAt(
                        now, claim.attemptCount());
                boolean retry = claimRepository.markRetry(
                        claim.artifactId(), claim.attemptCount(),
                        nextAttempt, safeError, now);
                return retry ? WorkerOutcome.RETRY : WorkerOutcome.LOST_OWNERSHIP;
            case INTERRUPTED:
            default:
                return WorkerOutcome.INTERRUPTED;
        }
    }

    /**
     * Phase-4 timing breakdown. Logs the three stage timings so
     * the operator can determine whether future slowness is on
     * Supabase download, LibreOffice conversion or Supabase
     * upload. Only millis are logged; URLs, paths, tokens and
     * bytes are intentionally NEVER logged here.
     *
     * @param claim              the artifact being processed
     * @param tDownloadStartNanos anchor captured BEFORE the
     *                           Supabase download call; zero when
     *                           the call was never reached
     * @param tDownloadEndNanos  anchor captured AFTER the download
     *                           call; zero when the call failed or
     *                           was never reached
     * @param tConvertEndNanos   anchor captured AFTER the
     *                           conversion call; zero when the call
     *                           failed or was never reached
     * @param tUploadStartNanos  anchor captured BEFORE the Supabase
     *                           upload call; zero when the call was
     *                           never reached
     * @param tUploadEndNanos    anchor captured AFTER the upload
     *                           call; zero when the call failed or
     *                           was never reached
     */
    private static void logStageTimings(
            DocumentPreviewArtifactClaim claim,
            long tDownloadStartNanos,
            long tDownloadEndNanos,
            long tConvertEndNanos,
            long tUploadStartNanos,
            long tUploadEndNanos) {
        if (!log.isInfoEnabled()) {
            return;
        }
        long sourceDownloadMs = tDownloadEndNanos > 0L
                && tDownloadStartNanos > 0L
                        ? (tDownloadEndNanos - tDownloadStartNanos)
                                / 1_000_000L
                        : -1L;
        long officeConvertMs = tConvertEndNanos > 0L
                && tDownloadEndNanos > 0L
                        ? (tConvertEndNanos - tDownloadEndNanos)
                                / 1_000_000L
                        : -1L;
        long artifactUploadMs = tUploadEndNanos > 0L
                && tUploadStartNanos > 0L
                        ? (tUploadEndNanos - tUploadStartNanos)
                                / 1_000_000L
                        : -1L;
        log.info("stage-timing id={} attempt={} sourceDownloadMs={} "
                        + "officeConvertMs={} artifactUploadMs={}",
                claim.artifactId(),
                claim.attemptCount(),
                sourceDownloadMs, officeConvertMs, artifactUploadMs);
    }

    /**
     * Internal interruption signal. Thrown by
     * {@link #ensureNotInterrupted(String, String, String)} when the
     * current thread is interrupted. It carries the {@code stage}
     * identifier and, when set, the exact attempt-owned {@code (bucket,
     * path)} coordinates of an object the worker has already uploaded
     * but has not yet marked {@code READY}.
     *
     * <p>The signal is intentionally not a {@link RuntimeException}
     * subclass that could be confused with a Supabase or LibreOffice
     * failure; the worker translates it to
     * {@link WorkerOutcome#INTERRUPTED} at the top of
     * {@link #process}.</p>
     */
    private static final class InterruptionSignal extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final String stage;
        private final String uploadedBucket;
        private final String uploadedPath;

        InterruptionSignal(String stage,
                           String uploadedBucket,
                           String uploadedPath) {
            super("Interruption at " + stage);
            this.stage = stage;
            this.uploadedBucket = uploadedBucket;
            this.uploadedPath = uploadedPath;
        }

        String stage() {
            return stage;
        }

        String uploadedBucket() {
            return uploadedBucket;
        }

        String uploadedPath() {
            return uploadedPath;
        }
    }

    /**
     * Check the current thread's interrupt flag. When the flag is set,
     * throw {@link InterruptionSignal} WITHOUT clearing the flag; the
     * outer {@link #process(DocumentPreviewArtifactClaim, LocalDateTime)}
     * re-asserts the flag so the worker scheduler can observe it.
     *
     * @param stage            human-readable identifier of the
     *                         checkpoint; used only for logs
     * @param uploadedBucket   when non-null, indicates an upload has
     *                         already succeeded at this attempt and
     *                         the orphaned-upload cleanup MUST be
     *                         enqueued on interruption
     * @param uploadedPath     the exact attempt-owned path the worker
     *                         just wrote (required when
     *                         {@code uploadedBucket} is non-null)
     */
    private static void ensureNotInterrupted(String stage,
                                              String uploadedBucket,
                                              String uploadedPath) {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptionSignal(stage, uploadedBucket, uploadedPath);
        }
    }

    /**
     * Result of {@link #validateOfficeSource(DocumentFile)}. Encapsulates
     * the outcome of all pre-download metadata checks so the caller can
     * distinguish the failure reason and produce an accurate error code.
     *
     * @param fileType  the resolved {@link AllowedDocumentFileType#DOC} or
     *                  {@link AllowedDocumentFileType#DOCX}; {@code null}
     *                  when validation failed
     * @param errorCode the bounded error code for {@link #applyDecision};
     *                  one of {@code O3_SOURCE_MISSING},
     *                  {@code O3_UNSUPPORTED_SOURCE},
     *                  {@code O3_METADATA_INCOMPLETE},
     *                  or {@code O3_MIME_MISMATCH}; {@code null} when
     *                  validation succeeded
     */
    record OfficeSourceValidation(
            AllowedDocumentFileType fileType,
            String errorCode) {

        private static final OfficeSourceValidation MISSING_BUCKET =
                new OfficeSourceValidation(null, "O3_METADATA_INCOMPLETE");
        private static final OfficeSourceValidation MISSING_PATH =
                new OfficeSourceValidation(null, "O3_METADATA_INCOMPLETE");
        private static final OfficeSourceValidation UNSUPPORTED =
                new OfficeSourceValidation(null, "O3_UNSUPPORTED_SOURCE");
        private static final OfficeSourceValidation MIME_MISMATCH =
                new OfficeSourceValidation(null, "O3_MIME_MISMATCH");

        /**
         * Checks all authoritative metadata on the given
         * {@link DocumentFile} before any storage I/O.
         *
         * <p>Validation order:</p>
         * <ol>
         *   <li>Storage bucket is non-null and non-blank.</li>
         *   <li>Object path is non-null and non-blank.</li>
         *   <li>Extension resolves to DOC or DOCX only (Phase O3).</li>
         *   <li>Declared MIME type is consistent with the extension
         *       (if a MIME type is stored).</li>
         * </ol>
         *
         * <p>When any check fails, no storage download is attempted.</p>
         *
         * <p>The MIME normalisation mirrors the existing
         * {@link com.cmcu.itstudy.service.impl.PaidUploadFileValidatorServiceImpl}
         * contract: trim whitespace, compare case-insensitively, use the
         * exact canonical strings from
         * {@link AllowedDocumentFileType#fromMimeType(String)}.</p>
         */
        static OfficeSourceValidation from(DocumentFile source) {
            if (source == null) {
                return MISSING_BUCKET;
            }
            // 1. Bucket presence.
            if (source.getStorageBucket() == null
                    || source.getStorageBucket().isBlank()) {
                return MISSING_BUCKET;
            }
            // 2. Object path presence.
            if (source.getStoragePath() == null
                    || source.getStoragePath().isBlank()) {
                return MISSING_PATH;
            }
            // 3. Extension must resolve to DOC or DOCX.
            String ext = source.getFileExtension();
            AllowedDocumentFileType byExt =
                    AllowedDocumentFileType.fromExtension(ext).orElse(null);
            if (byExt != AllowedDocumentFileType.DOC
                    && byExt != AllowedDocumentFileType.DOCX) {
                return UNSUPPORTED;
            }
            // 4. MIME consistency (if a MIME type is stored).
            //    The MIME field may be null in legacy rows.
            //    When present it must agree with the extension.
            String mime = source.getMimeType();
            if (mime != null && !mime.isBlank()) {
                AllowedDocumentFileType byMime =
                        AllowedDocumentFileType.fromMimeType(mime).orElse(null);
                if (byMime != byExt) {
                    return MIME_MISMATCH;
                }
            }
            return new OfficeSourceValidation(byExt, null);
        }
    }

    private WorkerOutcome compensateOrphanedUpload(
            DocumentPreviewArtifactClaim claim,
            String bucket,
            String path,
            LocalDateTime now,
            WorkerOutcome originalOutcome) {
        // SAFETY: the cleanup task MUST target the exact attempt-owned
        // path the worker just uploaded. It MUST NEVER target the
        // original DocumentFile bucket/path or any other attempt's
        // preview path. The bucket and path arguments are produced by
        // DocumentPreviewPathBuilder, which embeds
        // claim.artifactId() and claim.attemptCount().
        try {
            cleanupTaskService.enqueueNewObjectCleanup(
                    bucket, path,
                    StorageCleanupReason.WORKER_PARTIAL_FAILURE,
                    null, claim.documentFileId());
        } catch (RuntimeException e) {
            // The compensation is logged safely. It MUST NOT cause an
            // unguarded overwrite of the artifact state and MUST NOT
            // clear the interrupt flag.
            log.warn("Compensation cleanup enqueue failed for id={}",
                    claim.artifactId());
        }
        return originalOutcome == WorkerOutcome.INTERRUPTED
                ? WorkerOutcome.INTERRUPTED
                : WorkerOutcome.LOST_OWNERSHIP;
    }

    /** Worker-level outcome of a single artifact processing. */
    public enum WorkerOutcome {
        READY,
        RETRY,
        DEAD,
        LOST_OWNERSHIP,
        INTERRUPTED
    }

    /**
     * @return the {@link Clock} injected at construction. Exposed so
     *         the worker scheduler can read the same clock; not used
     *         by the processor once a cycle-supplied {@code now} has
     *         been delivered.
     */
    public Clock clock() {
        return clock;
    }
}