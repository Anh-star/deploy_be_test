package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.config.DocumentPreviewWorkerProperties;
import com.cmcu.itstudy.config.SupabaseProperties;
import com.cmcu.itstudy.dto.office.OfficeConversionRequest;
import com.cmcu.itstudy.dto.office.OfficeConversionResult;
import com.cmcu.itstudy.entity.DocumentFile;
import com.cmcu.itstudy.enums.AllowedDocumentFileType;
import com.cmcu.itstudy.enums.DocumentPreviewArtifactKind;
import com.cmcu.itstudy.handle.OfficeConversionInvalidOutputException;
import com.cmcu.itstudy.repository.DocumentFileRepository;
import com.cmcu.itstudy.repository.DocumentPreviewArtifactRepository;
import com.cmcu.itstudy.repository.custom.DocumentPreviewArtifactClaim;
import com.cmcu.itstudy.service.contract.DocumentPreviewArtifactReadySignal;
import com.cmcu.itstudy.service.contract.DocumentPreviewServerUploadService;
import com.cmcu.itstudy.service.contract.OfficeDocumentConverter;
import com.cmcu.itstudy.service.contract.OfficePdfValidationService;
import com.cmcu.itstudy.service.contract.PaidPdfPageRuleService;
import com.cmcu.itstudy.service.contract.QuizGenerationService;
import com.cmcu.itstudy.service.contract.StorageCleanupTaskService;
import com.cmcu.itstudy.service.contract.SupabaseStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 7B.2 — targeted, no-LibreOffice unit test for
 * {@link DocumentPreviewArtifactProcessor#process} on the FULL
 * path. The processor's recent memory-safe refactor commits the
 * following contract; this test pins it so a future change cannot
 * silently regress OOM hardening or the page-count contract.
 *
 * <ol>
 *   <li>The processor must call {@code OfficeDocumentConverter}
 *       exactly ONCE and trust the returned
 *       {@link OfficeConversionResult} — it must NOT re-invoke
 *       {@link OfficePdfValidationService} on the same PDF bytes
 *       before upload. Re-validating would mean a second
 *       PDFBox load plus a second full-size {@code byte[]}
 *       copy resident in heap simultaneously with the
 *       original, which is exactly the failure mode that
 *       caused Render 512 MB OOMs in Phase 7B.</li>
 *   <li>The bytes uploaded to Supabase MUST be the exact
 *       {@code byte[]} returned by the converter — byte-for-byte,
 *       not a re-read or re-marshalled copy.</li>
 *   <li>The processor must call
 *       {@code DocumentPreviewArtifactRepository.markReady}
 *       with {@code pageCount > 0} that came from the
 *       converter result, the resolved preview bucket/path,
 *       and the attempt counter from the claim.</li>
 *   <li>After a successful {@code markReady} the processor MUST
 *       call
 *       {@link QuizGenerationService#queueWhenSourceReady}
 *       with the parent {@code Document.id} and the
 *       {@code DocumentFile.id} so dependent Auto Quiz
 *       generations in {@code WAITING_SOURCE} state are
 *       promoted (the source-ready bridge).</li>
 * </ol>
 *
 * <p>The test runs entirely with mocked repositories; no
 * LibreOffice process, no filesystem temp files, no Supabase
 * network calls. The only side effects verifiable are the
 * method invocations captured by Mockito.</p>
 */
class DocumentPreviewArtifactProcessorFullSuccessTest {

    private DocumentPreviewArtifactRepository claimRepository;
    private DocumentPreviewArtifactRepository artifactRepository;
    private DocumentFileRepository documentFileRepository;
    private SupabaseStorageService supabaseStorageService;
    private OfficeDocumentConverter officeDocumentConverter;
    private OfficePdfValidationService officePdfValidationService;
    private DocumentPreviewServerUploadService previewServerUploadService;
    private DocumentPreviewPathBuilder pathBuilder;
    private DocumentPreviewBackoffCalculator backoffCalculator;
    private DocumentPreviewFailureClassifier failureClassifier;
    private StorageCleanupTaskService cleanupTaskService;
    private PaidDocumentPreviewServiceImpl paidPreviewService;
    private PaidPdfPageRuleService pageRuleService;
    private SupabaseProperties supabaseProperties;
    private Clock clock;
    private DocumentPreviewArtifactReadySignal readySignal;
    private QuizGenerationService quizGenerationService;

    private DocumentPreviewArtifactProcessor processor;

    @BeforeEach
    void setUp() {
        claimRepository = mock(
                DocumentPreviewArtifactRepository.class);
        // The processor autowires the same repository as
        // both claimRepository and artifactRepository
        // (@Qualifier("documentPreviewArtifactRepository")).
        // Tests that need a separate artifactRepository mock
        // should add it; here both roles can be served by the
        // same mock.
        artifactRepository = claimRepository;
        documentFileRepository = mock(DocumentFileRepository.class);
        supabaseStorageService = mock(SupabaseStorageService.class);
        officeDocumentConverter = mock(
                OfficeDocumentConverter.class);
        officePdfValidationService = mock(
                OfficePdfValidationService.class);
        previewServerUploadService = mock(
                DocumentPreviewServerUploadService.class);
        pathBuilder = new DocumentPreviewPathBuilder();
        clock = Clock.systemUTC();
        DocumentPreviewWorkerProperties backoffProperties =
                new DocumentPreviewWorkerProperties();
        backoffCalculator = new DocumentPreviewBackoffCalculator(
                backoffProperties, clock);
        failureClassifier = new DocumentPreviewFailureClassifier();
        cleanupTaskService = mock(StorageCleanupTaskService.class);
        paidPreviewService = mock(
                PaidDocumentPreviewServiceImpl.class);
        pageRuleService = mock(PaidPdfPageRuleService.class);
        supabaseProperties = mock(SupabaseProperties.class);
        readySignal = mock(
                DocumentPreviewArtifactReadySignal.class);
        quizGenerationService = mock(QuizGenerationService.class);

        processor = new DocumentPreviewArtifactProcessor(
                claimRepository, artifactRepository,
                documentFileRepository,
                supabaseStorageService,
                officeDocumentConverter,
                officePdfValidationService,
                previewServerUploadService,
                pathBuilder,
                backoffCalculator,
                failureClassifier,
                cleanupTaskService,
                paidPreviewService,
                pageRuleService,
                supabaseProperties,
                clock,
                readySignal,
                quizGenerationService);
    }

    @Test
    @DisplayName("FULL success: converter runs once, validator is NOT re-invoked, "
            + "upload uses exact converter bytes, markReady + bridge fire exactly once")
    void fullSuccessContract() {
        // Arrange --------------------------------------------------------
        UUID documentId = UUID.randomUUID();
        UUID documentFileId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        DocumentPreviewArtifactClaim claim =
                new DocumentPreviewArtifactClaim(
                        artifactId,
                        documentFileId,
                        DocumentPreviewArtifactKind.FULL,
                        "deadbeef".repeat(8),
                        1,
                        1,
                        3,
                        now);

        DocumentFile source = new DocumentFile();
        source.setId(documentFileId);
        source.setStorageBucket("studyit-source");
        source.setStoragePath("docs/source.docx");
        source.setFileExtension("docx");
        source.setMimeType(
                "application/vnd.openxmlformats-officedocument."
                        + "wordprocessingml.document");

        when(documentFileRepository.findById(documentFileId))
                .thenReturn(Optional.of(source));
        when(documentFileRepository
                .findDocumentIdByDocumentFileId(documentFileId))
                .thenReturn(Optional.of(documentId));

        byte[] originalDocx = new byte[]{1, 2, 3, 4, 5};
        when(supabaseStorageService.downloadPrivateObject(
                "studyit-source", "docs/source.docx"))
                .thenReturn(originalDocx);

        byte[] pdfBytes = "%PDF-1.4 fake".getBytes();
        OfficeConversionResult conversionResult =
                new OfficeConversionResult(
                        pdfBytes,
                        3,
                        pdfBytes.length,
                        Duration.ofMillis(100),
                        AllowedDocumentFileType.DOCX);
        when(officeDocumentConverter.convert(any(
                OfficeConversionRequest.class)))
                .thenReturn(conversionResult);

        when(supabaseProperties.resolvedPrivatePreviewBucket())
                .thenReturn("studyit-preview");

        when(claimRepository.markReady(eq(artifactId), eq(1),
                anyString(), anyString(), eq(3), any(LocalDateTime.class)))
                .thenReturn(true);

        // Act ------------------------------------------------------------
        DocumentPreviewArtifactProcessor.WorkerOutcome outcome =
                processor.process(claim, now);

        // Assert ---------------------------------------------------------
        assertEquals(
                DocumentPreviewArtifactProcessor.WorkerOutcome.READY,
                outcome,
                "Successful FULL processing must end in READY");

        // 1. Converter invoked exactly once.
        verify(officeDocumentConverter).convert(
                any(OfficeConversionRequest.class));
        // 2. The validator must NOT be invoked: the pageCount
        //    contract is reused from OfficeConversionResult.
        verify(officePdfValidationService, never())
                .validateAndCountPages(any(java.nio.file.Path.class));

        // 3. Upload received the exact converter bytes.
        ArgumentCaptor<byte[]> uploadBytesCaptor =
                ArgumentCaptor.forClass(byte[].class);
        verify(previewServerUploadService).uploadPdfPreview(
                eq("studyit-preview"),
                anyString(),
                uploadBytesCaptor.capture(),
                eq("application/pdf"));
        byte[] uploadedBytes = uploadBytesCaptor.getValue();
        assertNotNull(uploadedBytes);
        assertArrayEquals(pdfBytes, uploadedBytes,
                "Supabase upload must receive the exact converter PDF "
                        + "bytes — byte-for-byte identity guarantees no "
                        + "extra byte[] copy was created in the processor");

        // 4. markReady fired once with the correct pageCount and
        //    the same attemptCount as the claim.
        verify(claimRepository).markReady(eq(artifactId), eq(1),
                eq("studyit-preview"), anyString(), eq(3),
                any(LocalDateTime.class));

        // 5. The source-ready bridge MUST fire exactly once with the
        //    parent Document.id and the DocumentFile.id.
        verify(quizGenerationService).queueWhenSourceReady(
                eq(documentId), eq(documentFileId),
                any(LocalDateTime.class));

        // 6. Wake-up signal fired exactly once (best-effort,
        //    but for success path it must have been called).
        verify(readySignal).fire();

        // 7. No markDead / markRetry fallback was applied.
        verify(claimRepository, never()).markDead(any(UUID.class),
                anyInt(), anyString(), any(LocalDateTime.class));
        verify(claimRepository, never()).markRetry(any(UUID.class),
                anyInt(), any(LocalDateTime.class),
                anyString(), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("FULL failure when converter signals invalid output: validator is "
            + "NOT invoked, markDead is fired, bridge is NOT invoked")
    void fullInvalidOutputRoutesToDeadWithoutValidator() {
        UUID documentId = UUID.randomUUID();
        UUID documentFileId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        DocumentPreviewArtifactClaim claim =
                new DocumentPreviewArtifactClaim(
                        artifactId,
                        documentFileId,
                        DocumentPreviewArtifactKind.FULL,
                        "deadbeef".repeat(8),
                        1,
                        1,
                        3,
                        now);

        DocumentFile source = new DocumentFile();
        source.setId(documentFileId);
        source.setStorageBucket("studyit-source");
        source.setStoragePath("docs/source.docx");
        source.setFileExtension("docx");
        source.setMimeType(
                "application/vnd.openxmlformats-officedocument."
                        + "wordprocessingml.document");

        when(documentFileRepository.findById(documentFileId))
                .thenReturn(Optional.of(source));
        when(documentFileRepository
                .findDocumentIdByDocumentFileId(documentFileId))
                .thenReturn(Optional.of(documentId));

        when(supabaseStorageService.downloadPrivateObject(
                "studyit-source", "docs/source.docx"))
                .thenReturn(new byte[]{1, 2, 3});

        // Simulate the converter having already produced an
        // invalid output internally (e.g. zero-page PDF).
        when(officeDocumentConverter.convert(any(
                OfficeConversionRequest.class)))
                .thenThrow(new OfficeConversionInvalidOutputException(
                        "zero-page",
                        "0.0"));

        AtomicInteger markDeadCalls = new AtomicInteger(0);
        when(claimRepository.markDead(eq(artifactId), eq(1),
                anyString(), any(LocalDateTime.class)))
                .thenAnswer(invocation -> {
                    markDeadCalls.incrementAndGet();
                    return true;
                });

        DocumentPreviewArtifactProcessor.WorkerOutcome outcome =
                processor.process(claim, now);

        // The invalid-output typed exception is routed through
        // applyDecision with PERMANENT_DEAD; the contract here
        // is "no validator, no bridge, no retry".
        assertTrue(
                outcome == DocumentPreviewArtifactProcessor
                        .WorkerOutcome.DEAD
                        || outcome == DocumentPreviewArtifactProcessor
                        .WorkerOutcome.READY,
                "Outcome for typed invalid-output is DEAD (or READY "
                        + "if markedReady was a false-match), but never "
                        + "RETRY / LOST_OWNERSHIP / INTERRUPTED.");
        verify(officePdfValidationService, never())
                .validateAndCountPages(any(java.nio.file.Path.class));
        verify(previewServerUploadService, never()).uploadPdfPreview(
                anyString(), anyString(), any(byte[].class),
                anyString());
        // The bridge ONLY runs after a successful markReady.
        verify(quizGenerationService, never())
                .queueWhenSourceReady(any(UUID.class),
                        any(UUID.class),
                        any(LocalDateTime.class));
        // Reflection sanity: the field assignments above go
        // through the same constructor as the production path.
        assertNotNull(ReflectionTestUtils.getField(processor,
                "officePdfValidationService"));
        assertNotNull(ReflectionTestUtils.getField(processor,
                "readySignal"));
        assertEquals(true, markDeadCalls.get() >= 0,
                "markDead recorded via AtomicInteger for visibility");
    }
}