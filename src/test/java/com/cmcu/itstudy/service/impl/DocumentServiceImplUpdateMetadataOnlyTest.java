package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.dto.document.DocumentCardDto;
import com.cmcu.itstudy.dto.document.DocumentUpdateRequestDto;
import com.cmcu.itstudy.entity.Category;
import com.cmcu.itstudy.entity.Document;
import com.cmcu.itstudy.entity.DocumentFile;
import com.cmcu.itstudy.entity.Tag;
import com.cmcu.itstudy.entity.User;
import com.cmcu.itstudy.enums.DocumentStatus;
import com.cmcu.itstudy.enums.FileType;
import com.cmcu.itstudy.enums.PaymentStatus;
import com.cmcu.itstudy.repository.CategoryRepository;
import com.cmcu.itstudy.repository.DocumentFileRepository;
import com.cmcu.itstudy.repository.DocumentPreviewArtifactRepository;
import com.cmcu.itstudy.repository.DocumentQuizRepository;
import com.cmcu.itstudy.repository.DocumentReportRepository;
import com.cmcu.itstudy.repository.DocumentRepository;
import com.cmcu.itstudy.repository.DocumentTagRepository;
import com.cmcu.itstudy.repository.PaymentRepository;
import com.cmcu.itstudy.repository.QuizGenerationRepository;
import com.cmcu.itstudy.repository.QuizQuestionRepository;
import com.cmcu.itstudy.repository.TagRepository;
import com.cmcu.itstudy.service.contract.DocumentAccessService;
import com.cmcu.itstudy.service.contract.QuizGenerationService;
import com.cmcu.itstudy.service.contract.TransactionalDocumentCrudService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 7B.6A — targeted, repository-mocked unit test for the
 * metadata-only edit contract on {@link DocumentServiceImpl#updateDocument}.
 *
 * <p>The previous Phase 7B.6 only relaxed the FRONTEND entry guard so the
 * edit form could open. It did not fix the strict Bean Validation on
 * {@code DocumentUpdateRequestDto.thumbnailUrl}, {@code documentUrl},
 * {@code fileName}, {@code fileSizeBytes} — all of which were
 * {@code @NotBlank} / {@code @NotNull}. As a result a PENDING owner
 * document with no cover could open the edit form but the next metadata
 * Save still failed with HTTP 400.</p>
 *
 * <p>Phase 7B.6A relaxes those annotations AND adds preserve-on-blank
 * semantics inside the service so a metadata-only PUT never wipes the
 * persisted cover / file metadata. This test pins every required
 * end-to-end case (A through I) listed in the user-facing spec.</p>
 *
 * <p>The test runs entirely with Mockito-mocked repositories; no Spring
 * context, no DB, no remote calls.</p>
 */
class DocumentServiceImplUpdateMetadataOnlyTest {

    private DocumentRepository documentRepository;
    private DocumentTagRepository documentTagRepository;
    private CategoryRepository categoryRepository;
    private TagRepository tagRepository;
    private DocumentFileRepository documentFileRepository;
    private PaymentRepository paymentRepository;
    private DocumentReportRepository documentReportRepository;
    private TransactionalDocumentCrudService transactionalDocumentCrudService;
    private QuizGenerationService quizGenerationService;
    private DocumentQuizRepository documentQuizRepository;
    private QuizGenerationRepository quizGenerationRepository;
    private QuizQuestionRepository quizQuestionRepository;
    private DocumentAccessService documentAccessService;
    private DocumentPreviewArtifactRepository documentPreviewArtifactRepository;

    private DocumentServiceImpl service;

    private User owner;
    private Category category;
    private UUID documentId;
    private UUID ownerId;

    @BeforeEach
    void setUp() {
        documentRepository = mock(DocumentRepository.class);
        documentTagRepository = mock(DocumentTagRepository.class);
        categoryRepository = mock(CategoryRepository.class);
        tagRepository = mock(TagRepository.class);
        documentFileRepository = mock(DocumentFileRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        documentReportRepository = mock(DocumentReportRepository.class);
        transactionalDocumentCrudService = mock(TransactionalDocumentCrudService.class);
        quizGenerationService = mock(QuizGenerationService.class);
        documentQuizRepository = mock(DocumentQuizRepository.class);
        quizGenerationRepository = mock(QuizGenerationRepository.class);
        quizQuestionRepository = mock(QuizQuestionRepository.class);
        documentAccessService = mock(DocumentAccessService.class);
        documentPreviewArtifactRepository = mock(DocumentPreviewArtifactRepository.class);

        service = new DocumentServiceImpl(
                documentRepository,
                documentTagRepository,
                categoryRepository,
                tagRepository,
                documentFileRepository,
                paymentRepository,
                documentReportRepository,
                transactionalDocumentCrudService,
                quizGenerationService,
                documentQuizRepository,
                quizGenerationRepository,
                quizQuestionRepository,
                documentAccessService,
                documentPreviewArtifactRepository);

        ownerId = UUID.randomUUID();
        owner = User.builder()
                .id(ownerId)
                .email("owner@example.com")
                .fullName("Owner Name")
                .build();

        category = Category.builder()
                .id(UUID.randomUUID())
                .name("Lập trình")
                .slug("lap-trinh")
                .build();
        when(categoryRepository.findByName("Lập trình"))
                .thenReturn(Optional.of(category));

        documentId = UUID.randomUUID();

        // Default: no DocumentFile row unless a specific case adds one.
        when(documentFileRepository.findByDocumentIdAndPrimaryTrue(any(UUID.class)))
                .thenReturn(Optional.empty());
        when(documentFileRepository.save(any(DocumentFile.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Default: tag round-trip — every requested tag already exists in
        // the repository so the test can focus on the metadata-only edit
        // semantics without exercising tag-creation paths.
        when(tagRepository.findBySlug(any(String.class)))
                .thenAnswer(inv -> {
                    String slug = inv.getArgument(0);
                    return Optional.of(Tag.builder()
                            .id(UUID.randomUUID())
                            .name("tag:" + slug)
                            .slug(slug)
                            .build());
                });
        when(tagRepository.save(any(Tag.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(documentTagRepository.saveAll(anyList()))
                .thenAnswer(inv -> new ArrayList<>(inv.getArgument(0)));

        // Default: pricing NOT locked (no SUCCESS payments from a
        // non-owner buyer).
        when(paymentRepository.existsByDocumentIdAndStatusAndUserIdNot(
                any(UUID.class), any(PaymentStatus.class), any(UUID.class)))
                .thenReturn(false);

        // Default: no duplicate slug — metadata-only edits never collide
        // because the title is unchanged or already unique.
        when(documentRepository.existsBySlugAndIdNot(any(String.class), any(UUID.class)))
                .thenReturn(false);
    }

    // -----------------------------------------------------------------
    // Shared fixtures
    // -----------------------------------------------------------------

    private Document basePendingDocument(String title, String thumbnailUrl,
                                          String fileUrl, String fileName, Long fileSize,
                                          boolean isPaid, Long price) {
        return Document.builder()
                .id(documentId)
                .title(title)
                .slug("test-slug")
                .description("Original long-enough description that easily satisfies the 80-char minimum for the metadata-only edit flow.")
                .thumbnailUrl(thumbnailUrl)
                .fileUrl(fileUrl)
                .fileName(fileName)
                .fileType(FileType.PDF)
                .fileSize(fileSize)
                .category(category)
                .createdBy(owner)
                .status(DocumentStatus.PENDING)
                .isPaid(isPaid)
                .price(price)
                .documentTags(new java.util.HashSet<>())
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now().minusDays(1))
                .build();
    }

    private DocumentUpdateRequestDto baseMetadataOnlyUpdate(String newTitle) {
        // Mimic the FE submitUpdateDocument payload for a metadata-only
        // edit: thumbnailUrl / documentUrl are blank strings (FE
        // round-trips "" for missing optional asset values), fileName +
        // fileSizeBytes still hold the existing values because FE cached
        // them at preload, and storagePath is null (no replacement file).
        return baseMetadataOnlyUpdate(newTitle, "original.pdf", 1024L);
    }

    private DocumentUpdateRequestDto baseMetadataOnlyUpdate(String newTitle,
                                                             String existingFileName,
                                                             long existingFileSize) {
        return DocumentUpdateRequestDto.builder()
                .title(newTitle)
                .description("New long-enough description that easily satisfies the 80-char minimum for the metadata-only edit flow.")
                .category("Lập trình")
                .tags(List.of("java"))
                .documentUrl("")
                .storagePath(null)
                .thumbnailUrl("")
                .fileName(existingFileName)
                .fileSizeBytes(existingFileSize)
                .isPaid(false)
                .price(null)
                .build();
    }

    private void stubGetById(Document document) {
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
    }

    private void stubDocumentSave(Document document) {
        when(documentRepository.save(any(Document.class))).thenReturn(document);
    }

    // =================================================================
    // CASE A — PENDING owner document, existing thumbnail = null,
    //          no new thumbnail, change title only
    // =================================================================
    @Test
    @DisplayName("CASE A: PENDING + null thumbnail + metadata-only title change → "
            + "thumbnail remains null, file metadata unchanged, title updated")
    void caseA_pendingNullThumbnail_titleOnlyChange() {
        Document existing = basePendingDocument(
                "Original Title With Plenty Of Length",
                /* thumbnailUrl */ null,
                /* fileUrl      */ "https://example.com/original.pdf",
                /* fileName     */ "original.pdf",
                /* fileSize     */ 1024L,
                /* isPaid       */ false,
                /* price        */ null);
        stubGetById(existing);
        stubDocumentSave(existing);

        DocumentUpdateRequestDto dto = baseMetadataOnlyUpdate("New Title With Plenty Of Length Too");

        DocumentCardDto response = service.updateDocument(documentId, dto, owner);

        // Title changed, description updated.
        assertEquals("New Title With Plenty Of Length Too", existing.getTitle());
        // Thumbnail preserved as null — not wiped by the empty-string
        // round-trip from the FE.
        assertNull(existing.getThumbnailUrl(),
                "PENDING document with no cover must remain cover-less");
        // File URL/name/size preserved verbatim — the FE's "" round-trip
        // must NOT clobber the persisted file metadata.
        assertEquals("https://example.com/original.pdf", existing.getFileUrl());
        assertEquals("original.pdf", existing.getFileName());
        assertEquals(1024L, existing.getFileSize());
        // Status / ownership untouched.
        assertEquals(DocumentStatus.PENDING, existing.getStatus());
        assertEquals(owner.getId(), existing.getCreatedBy().getId());
        // Response reflects the new state.
        assertNotNull(response);
        assertNull(response.getThumbnailUrl());
    }

    // =================================================================
    // CASE B — PENDING owner document, existing thumbnail = URL_A,
    //          no new thumbnail, change description
    // =================================================================
    @Test
    @DisplayName("CASE B: existing thumbnail URL_A + no new thumbnail + description change → "
            + "thumbnail preserved as URL_A, description updated")
    void caseB_existingThumbnail_preservedOnDescriptionChange() {
        Document existing = basePendingDocument(
                "Stable Title With Plenty Of Length",
                /* thumbnailUrl */ "https://cdn.example.com/cover-A.png",
                /* fileUrl      */ "https://example.com/file.pdf",
                /* fileName     */ "file.pdf",
                /* fileSize     */ 2048L,
                /* isPaid       */ false,
                /* price        */ null);
        stubGetById(existing);
        stubDocumentSave(existing);

        DocumentUpdateRequestDto dto = baseMetadataOnlyUpdate(
                "Stable Title With Plenty Of Length",
                "file.pdf",
                2048L);
        dto.setDescription("A brand new long description that is at least eighty characters long to satisfy the validator.");

        DocumentCardDto response = service.updateDocument(documentId, dto, owner);

        assertEquals("https://cdn.example.com/cover-A.png", existing.getThumbnailUrl(),
                "Existing thumbnail must remain when the FE sends an empty string");
        assertEquals("A brand new long description that is at least eighty characters long to satisfy the validator.",
                existing.getDescription());
        assertEquals("https://example.com/file.pdf", existing.getFileUrl());
        assertEquals("file.pdf", existing.getFileName());
        assertEquals(2048L, existing.getFileSize());
        assertEquals("https://cdn.example.com/cover-A.png", response.getThumbnailUrl());
    }

    // =================================================================
    // CASE C — existing thumbnail = URL_A, new thumbnail = URL_B
    // =================================================================
    @Test
    @DisplayName("CASE C: existing thumbnail URL_A + new thumbnail URL_B → "
            + "thumbnail becomes URL_B")
    void caseC_newThumbnail_replacesExisting() {
        Document existing = basePendingDocument(
                "Stable Title With Plenty Of Length",
                /* thumbnailUrl */ "https://cdn.example.com/cover-A.png",
                /* fileUrl      */ "https://example.com/file.pdf",
                /* fileName     */ "file.pdf",
                /* fileSize     */ 2048L,
                /* isPaid       */ false,
                /* price        */ null);
        stubGetById(existing);
        stubDocumentSave(existing);

        DocumentUpdateRequestDto dto = baseMetadataOnlyUpdate(
                "Stable Title With Plenty Of Length",
                "file.pdf",
                2048L);
        dto.setThumbnailUrl("https://cdn.example.com/cover-B.png");

        DocumentCardDto response = service.updateDocument(documentId, dto, owner);

        assertEquals("https://cdn.example.com/cover-B.png", existing.getThumbnailUrl(),
                "New non-blank thumbnail must overwrite the old one");
        assertEquals("https://example.com/file.pdf", existing.getFileUrl());
        assertEquals("file.pdf", existing.getFileName());
        assertEquals(2048L, existing.getFileSize());
        assertEquals("https://cdn.example.com/cover-B.png", response.getThumbnailUrl());
    }

    // =================================================================
    // CASE D — no replacement document file, metadata-only edit
    //          ⇒ existing DocumentFile row preserved
    // =================================================================
    @Test
    @DisplayName("CASE D: no replacement file + existing DocumentFile row → "
            + "DocumentFile storagePath / fileUrl / originalFileName / sizeBytes / extension unchanged")
    void caseD_existingDocumentFile_preservedOnMetadataOnlyEdit() {
        Document existing = basePendingDocument(
                "Stable Title With Plenty Of Length",
                /* thumbnailUrl */ null,
                /* fileUrl      */ null, // File metadata lives on DocumentFile
                /* fileName     */ null,
                /* fileSize     */ null,
                /* isPaid       */ false,
                /* price        */ null);
        stubGetById(existing);
        stubDocumentSave(existing);

        UUID documentFileId = UUID.randomUUID();
        DocumentFile primary = DocumentFile.builder()
                .id(documentFileId)
                .document(existing)
                .storageBucket("studyit-source")
                .storagePath("assets/UploadedDocuments/original.pdf")
                .fileUrl("https://example.com/signed-original.pdf?token=abc")
                .originalFileName("original.pdf")
                .fileExtension("pdf")
                .sizeBytes(8192L)
                .primary(true)
                .createdAt(LocalDateTime.now().minusDays(1))
                .build();
        when(documentFileRepository.findByDocumentIdAndPrimaryTrue(documentId))
                .thenReturn(Optional.of(primary));

        DocumentUpdateRequestDto dto = baseMetadataOnlyUpdate("Stable Title With Plenty Of Length");
        // FE round-trips with blanks for the file-shaped fields because
        // it could not preload the signed URL.
        dto.setDocumentUrl("");
        dto.setFileName("");
        dto.setFileSizeBytes(null);

        DocumentCardDto response = service.updateDocument(documentId, dto, owner);

        // DocumentFile row preserved verbatim — storagePath / fileUrl /
        // originalFileName / sizeBytes / fileExtension are NOT clobbered
        // by the FE's blank round-trip.
        assertEquals("assets/UploadedDocuments/original.pdf", primary.getStoragePath());
        assertEquals("https://example.com/signed-original.pdf?token=abc", primary.getFileUrl());
        assertEquals("original.pdf", primary.getOriginalFileName());
        assertEquals("pdf", primary.getFileExtension());
        assertEquals(8192L, primary.getSizeBytes());
        // DocumentFile save IS invoked — syncPrimaryDocumentFile always
        // persists the row after merging — but with the preserved values.
        verify(documentFileRepository).save(primary);
        // Response carries the preserved storagePath.
        assertEquals("assets/UploadedDocuments/original.pdf", response.getStoragePath());
    }

    // =================================================================
    // CASE E — document preload/preview URL is null but persisted file
    //          exists ⇒ metadata-only Save succeeds
    // =================================================================
    @Test
    @DisplayName("CASE E: FE preloaded documentUrl=null + persisted DocumentFile exists → "
            + "update succeeds, file remains intact")
    void caseE_nullPreviewUrl_metadataOnlySaveSucceeds() {
        Document existing = basePendingDocument(
                "Stable Title With Plenty Of Length",
                /* thumbnailUrl */ "https://cdn.example.com/cover.png",
                /* fileUrl      */ null,
                /* fileName     */ null,
                /* fileSize     */ null,
                /* isPaid       */ false,
                /* price        */ null);
        stubGetById(existing);
        stubDocumentSave(existing);

        DocumentFile primary = DocumentFile.builder()
                .id(UUID.randomUUID())
                .document(existing)
                .storageBucket("studyit-source")
                .storagePath("assets/UploadedDocuments/file.pdf")
                .fileUrl(null) // cached URL expired / unset on owner-detail
                .originalFileName("file.pdf")
                .fileExtension("pdf")
                .sizeBytes(4096L)
                .primary(true)
                .createdAt(LocalDateTime.now().minusDays(2))
                .build();
        when(documentFileRepository.findByDocumentIdAndPrimaryTrue(documentId))
                .thenReturn(Optional.of(primary));

        // FE could not preload documentUrl / fileName / fileSizeBytes
        // because the owner-detail returned null for those fields. The
        // FE therefore sends blanks / null. Storage path is also null
        // because the FE never round-trips storagePath (not exposed on
        // MyDocumentDetailDto).
        DocumentUpdateRequestDto dto = DocumentUpdateRequestDto.builder()
                .title("Stable Title With Plenty Of Length")
                .description("Updated description with at least eighty characters to satisfy the validator easily.")
                .category("Lập trình")
                .tags(List.of("python"))
                .documentUrl("")
                .storagePath(null)
                .thumbnailUrl("https://cdn.example.com/cover.png")
                .fileName("")
                .fileSizeBytes(null)
                .isPaid(false)
                .price(null)
                .build();

        DocumentCardDto response = service.updateDocument(documentId, dto, owner);

        // Persisted DocumentFile row was not destroyed.
        assertEquals("assets/UploadedDocuments/file.pdf", primary.getStoragePath());
        assertNull(primary.getFileUrl(), "Cached null URL stays null — not silently rewritten to \"\"");
        assertEquals("file.pdf", primary.getOriginalFileName());
        assertEquals(4096L, primary.getSizeBytes());
        assertEquals("assets/UploadedDocuments/file.pdf", response.getStoragePath());
        // The owner can still see the persisted storagePath even though
        // the FE round-tripped nothing for it.
    }

    // =================================================================
    // CASE F — different user's document ⇒ still denied
    // =================================================================
    @Test
    @DisplayName("CASE F: different user attempts to update → SecurityException, no save")
    void caseF_otherUser_updateDenied() {
        Document existing = basePendingDocument(
                "Stable Title With Plenty Of Length",
                "https://cdn.example.com/cover.png",
                "https://example.com/file.pdf",
                "file.pdf",
                2048L,
                false,
                null);
        stubGetById(existing);

        User other = User.builder()
                .id(UUID.randomUUID())
                .email("other@example.com")
                .fullName("Other")
                .build();

        DocumentUpdateRequestDto dto = baseMetadataOnlyUpdate("Stable Title With Plenty Of Length");

        SecurityException ex = assertThrows(SecurityException.class,
                () -> service.updateDocument(documentId, dto, other));
        assertTrue(ex.getMessage().toLowerCase().contains("permission"),
                "Ownership check must still reject non-owner edits");
        verify(documentRepository, never()).save(any(Document.class));
    }

    // =================================================================
    // CASE G — pricing locked and pricing changed ⇒ existing backend
    //          restriction remains
    // =================================================================
    @Test
    @DisplayName("CASE G: pricing locked (non-owner SUCCESS payment exists) + pricing changed → "
            + "DocumentPricingLockedException, no save")
    void caseG_pricingLocked_pricingChangeRejected() {
        Document existing = basePendingDocument(
                "Stable Title With Plenty Of Length",
                "https://cdn.example.com/cover.png",
                "https://example.com/file.pdf",
                "file.pdf",
                2048L,
                /* isPaid */ true,
                /* price */ 5000L);
        existing.setStatus(DocumentStatus.APPROVED);
        stubGetById(existing);

        when(paymentRepository.existsByDocumentIdAndStatusAndUserIdNot(
                eq(documentId), any(PaymentStatus.class), any(UUID.class)))
                .thenReturn(true);

        DocumentUpdateRequestDto dto = baseMetadataOnlyUpdate("Stable Title With Plenty Of Length");
        // Pricing changed: paid → paid but price changes.
        dto.setIsPaid(true);
        dto.setPrice(7000L);

        com.cmcu.itstudy.handle.DocumentPricingLockedException ex = assertThrows(
                com.cmcu.itstudy.handle.DocumentPricingLockedException.class,
                () -> service.updateDocument(documentId, dto, owner));
        assertTrue(ex.getMessage().toLowerCase().contains("mua")
                        || ex.getMessage().toLowerCase().contains("hình thức")
                        || ex.getMessage().toLowerCase().contains("giá"),
                "Pricing-lock guard must continue to reject pricing changes");
        verify(documentRepository, never()).save(any(Document.class));
    }

    // =================================================================
    // CASE H — metadata-only edit with unchanged Auto Quiz config ⇒
    //          no new QuizGeneration row requested
    // =================================================================
    @Test
    @DisplayName("CASE H: metadata-only edit (no Auto Quiz config touched) → "
            + "service does NOT call any auto-quiz create path")
    void caseH_metadataOnlyAutoQuizUntouched_noQuizCreated() {
        Document existing = basePendingDocument(
                "Stable Title With Plenty Of Length",
                "https://cdn.example.com/cover.png",
                "https://example.com/file.pdf",
                "file.pdf",
                2048L,
                false,
                null);
        stubGetById(existing);
        stubDocumentSave(existing);

        DocumentUpdateRequestDto dto = baseMetadataOnlyUpdate("Stable Title With Plenty Of Length");
        // No quiz fields in DocumentUpdateRequestDto — updateDocument is
        // not allowed to spawn a generation on a metadata-only edit.

        DocumentCardDto response = service.updateDocument(documentId, dto, owner);

        assertNotNull(response);
        verify(quizGenerationService, never())
                .enqueueForDocument(any(UUID.class), any(UUID.class), any(), anyInt(), any(), any(LocalDateTime.class));
    }

    // =================================================================
    // CASE I — FAILED Auto Quiz config genuinely changed ⇒ existing
    //          retry/new-generation behavior remains UNCHANGED by 7B.6A
    //          (this case documents the OUT-OF-SCOPE behaviour).
    // =================================================================
    @Test
    @DisplayName("CASE I: 7B.6A does NOT introduce or alter any auto-quiz retry "
            + "behaviour — the update path never touches quiz generations")
    void caseI_autoQuizGeneration_outOfScopeForUpdate() {
        // The previous Phase 6C / Multi Auto Quiz 2 retry flow lives on
        // separate /auto-quizzes endpoints and is intentionally untouched
        // here. This test asserts that the updateDocument code path does
        // not spawn or alter a QuizGeneration row — the contract is
        // preserved.
        Document existing = basePendingDocument(
                "Stable Title With Plenty Of Length",
                "https://cdn.example.com/cover.png",
                "https://example.com/file.pdf",
                "file.pdf",
                2048L,
                false,
                null);
        stubGetById(existing);
        stubDocumentSave(existing);

        DocumentUpdateRequestDto dto = baseMetadataOnlyUpdate("Stable Title With Plenty Of Length");

        service.updateDocument(documentId, dto, owner);

        verify(quizGenerationService, never()).queueWhenSourceReady(any(UUID.class), any(UUID.class), any());
        verify(quizGenerationService, never()).cancelForDocument(any(UUID.class), any(LocalDateTime.class));
        verify(quizGenerationService, never())
                .enqueueForDocument(any(UUID.class), any(UUID.class), any(), anyInt(), any(), any(LocalDateTime.class));
        verify(quizGenerationRepository, never()).save(any());
        verify(documentQuizRepository, never()).save(any());
    }

    // =================================================================
    // DTO contract smoke check — the bean-validation annotations are
    // the root cause of the Phase 7B.6 blocker. Pin them so a future
    // regression cannot re-tighten them and silently re-introduce the
    // HTTP 400 on a PENDING owner document.
    // =================================================================
    @Test
    @DisplayName("DTO contract: thumbnailUrl / documentUrl / fileName / fileSizeBytes "
            + "are NOT @NotBlank / @NotNull — Bean Validation accepts a null/blank round-trip")
    void dtoContract_assetFieldsAreOptional() {
        jakarta.validation.Validator validator =
                jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();

        DocumentUpdateRequestDto dto = DocumentUpdateRequestDto.builder()
                .title("Valid Title With Plenty Of Length")
                .description("A perfectly valid description that easily satisfies the eighty-character minimum.")
                .category("Lập trình")
                .tags(List.of("java"))
                .documentUrl(null)     // optional
                .storagePath(null)     // optional
                .thumbnailUrl(null)    // optional — was @NotBlank before Phase 7B.6A
                .fileName(null)        // optional — was @NotBlank before Phase 7B.6A
                .fileSizeBytes(null)   // optional — was @NotNull  before Phase 7B.6A
                .isPaid(false)
                .price(null)
                .build();

        var violations = validator.validate(dto);
        assertTrue(violations.isEmpty(),
                "DocumentUpdateRequestDto must accept null/blank asset fields for metadata-only PUT. "
                        + "Violations: " + violations.stream()
                        .map(v -> v.getPropertyPath() + " " + v.getMessage())
                        .collect(Collectors.joining("; ")));
    }
}
