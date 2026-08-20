package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.config.SupabaseProperties;
import com.cmcu.itstudy.dto.document.DocumentCardDto;
import com.cmcu.itstudy.dto.document.DocumentCreateRequestDto;
import com.cmcu.itstudy.entity.Category;
import com.cmcu.itstudy.entity.Document;
import com.cmcu.itstudy.entity.DocumentFile;
import com.cmcu.itstudy.entity.DocumentTag;
import com.cmcu.itstudy.entity.Tag;
import com.cmcu.itstudy.entity.User;
import com.cmcu.itstudy.enums.AllowedDocumentFileType;
import com.cmcu.itstudy.enums.DocumentStatus;
import com.cmcu.itstudy.enums.FileType;
import com.cmcu.itstudy.repository.CategoryRepository;
import com.cmcu.itstudy.repository.DocumentFileRepository;
import com.cmcu.itstudy.repository.DocumentRepository;
import com.cmcu.itstudy.repository.DocumentTagRepository;
import com.cmcu.itstudy.repository.TagRepository;
import com.cmcu.itstudy.service.contract.QuizGenerationService;
import com.cmcu.itstudy.service.contract.TransactionalDocumentCrudService;
import com.cmcu.itstudy.util.SlugUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Default implementation of {@link TransactionalDocumentCrudService}.
 *
 * <p>This bean owns the FREE document create transaction. Its body is
 * line-for-line equivalent to the FREE branch that used to live in
 * {@code DocumentServiceImpl#createDocument}; the relocation is
 * a pure refactor — field semantics, validation outcomes, and the
 * response shape are unchanged.
 *
 * <p>Paid document create does NOT live here. The paid path is composed
 * by the {@link com.cmcu.itstudy.service.contract.PaidDocumentUploadOrchestrator}
 * (NOT_SUPPORTED transaction) which calls a separate
 * {@link com.cmcu.itstudy.service.contract.TransactionalPaidDocumentBinder}
 * (REQUIRED transaction).
 *
 * <p>This service is forbidden from calling any
 * {@code com.cmcu.itstudy.service.contract.SupabaseStorageService}
 * method or any bean that performs a remote HTTP call.
 */
@Service
public class TransactionalDocumentCrudServiceImpl implements TransactionalDocumentCrudService {

    private final DocumentRepository documentRepository;
    private final DocumentTagRepository documentTagRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final DocumentFileRepository documentFileRepository;
    private final DocumentPreviewArtifactFactory artifactFactory;
    private final SupabaseProperties supabaseProperties;
    private final QuizGenerationService quizGenerationService;
    private final Clock clock;

    public TransactionalDocumentCrudServiceImpl(
            DocumentRepository documentRepository,
            DocumentTagRepository documentTagRepository,
            CategoryRepository categoryRepository,
            TagRepository tagRepository,
            DocumentFileRepository documentFileRepository,
            DocumentPreviewArtifactFactory artifactFactory,
            SupabaseProperties supabaseProperties,
            QuizGenerationService quizGenerationService,
            Clock clock) {
        this.documentRepository = documentRepository;
        this.documentTagRepository = documentTagRepository;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.documentFileRepository = documentFileRepository;
        this.artifactFactory = artifactFactory;
        this.supabaseProperties = supabaseProperties;
        this.quizGenerationService = quizGenerationService;
        this.clock = clock;
    }

    /**
     * Free-create transaction. Field semantics and response mapping match
     * the pre-Phase-C1 {@code DocumentServiceImpl#createDocument} so the
     * existing API contract for free documents is preserved.
     */
    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRED)
    public DocumentCardDto createFreeDocument(
            DocumentCreateRequestDto request,
            User currentUser) {

        // 1. Find or create Category
        Category category = categoryRepository.findByName(request.getCategory())
                .orElseThrow(() -> new NoSuchElementException(
                        "Category not found: " + request.getCategory()));

        // 2. Build the Document entity. The free path trusts the request:
        //    documentUrl and storagePath are required by the DTO-level
        //    cross-field predicate isPaidUploadShapeValid().
        boolean paid = Boolean.TRUE.equals(request.getIsPaid());
        Document document = Document.builder()
                .title(request.getTitle())
                // Globally-unique slug resolver. Existence check is
                // delegated to a lambda so this service stays free of any
                // direct repository knowledge of the slug uniqueness
                // rules. Soft-deleted rows still occupy their slug
                // (tbl_documents.slug is a plain UNIQUE), so a fresh
                // create with the same title gets a suffixed slug.
                .slug(SlugUtils.resolveUniqueSlug(
                        request.getTitle(),
                        documentRepository::existsBySlug))
                .description(request.getDescription())
                .fileUrl(request.getDocumentUrl())
                .fileName(request.getFileName())
                .fileSize(request.getFileSizeBytes())
                .thumbnailUrl(request.getThumbnailUrl())
                .category(category)
                .createdBy(currentUser)
                .updatedBy(currentUser)
                .status(DocumentStatus.PENDING)
                .viewCount(0L)
                .downloadCount(0L)
                .bookmarkCount(0L)
                .deleted(false)
                .isPaid(paid)
                .price(resolveDocumentPrice(paid, request.getPrice()))
                .build();

        document.setFileType(detectFileType(request.getFileName()));

        Document savedDocument = documentRepository.save(document);

        // 3. Tags
        Set<DocumentTag> documentTags = new HashSet<>();
        for (String tagName : request.getTags()) {
            String tagSlug = SlugUtils.resolveSlug(tagName, tagName);
            Tag tag = tagRepository.findBySlug(tagSlug)
                    .orElseGet(() -> tagRepository.save(Tag.builder()
                            .name(tagName)
                            .slug(tagSlug)
                            .build()));
            DocumentTag documentTag = DocumentTag.builder()
                    .documentId(savedDocument.getId())
                    .tagId(tag.getId())
                    .document(savedDocument)
                    .tag(tag)
                    .createdAt(LocalDateTime.now())
                    .build();
            documentTags.add(documentTag);
        }
        savedDocument.setDocumentTags(documentTags);
        documentTagRepository.saveAll(documentTags);

        // 4. Primary DocumentFile (public bucket row).
        DocumentFile primaryFile = documentFileRepository.save(buildPrimaryDocumentFile(
                savedDocument,
                request.getStoragePath(),
                request.getDocumentUrl(),
                request.getFileName(),
                request.getFileSizeBytes()));

        // 4.1. Phase QUIZ-AI-2B: persist the upload-time AI quiz intent
        //       as a tbl_quiz_generations row inside this same REQUIRED
        //       transaction. The boolean + integer pair was already
        //       validated by the DTO @AssertTrue and @Min/@Max. PDF →
        //       QUEUED; DOC/DOCX → WAITING_SOURCE; PPT/PPTX rejected.
        //       No n8n call, no signed URL, no scheduler.
        if (Boolean.TRUE.equals(request.getGenerateQuiz())) {
            AllowedDocumentFileType quizFileType =
                    AllowedDocumentFileType.fromExtension(primaryFile.getFileExtension())
                            .orElseThrow(() -> new IllegalArgumentException(
                                    QuizGenerationServiceImpl.UNSUPPORTED_AUTO_QUIZ_MESSAGE));
            if (quizFileType == AllowedDocumentFileType.PPT
                    || quizFileType == AllowedDocumentFileType.PPTX) {
                throw new IllegalArgumentException(
                        QuizGenerationServiceImpl.UNSUPPORTED_AUTO_QUIZ_MESSAGE);
            }
            quizGenerationService.enqueueForDocument(
                    savedDocument.getId(),
                    primaryFile.getId(),
                    quizFileType,
                    request.getQuizQuestionCount(),
                    null,
                    LocalDateTime.now(clock));
        }

        // 5. Bootstrap a FRESH-Office preview artifact (DOC / DOCX only).
        //    The factory joins the existing REQUIRED transaction via
        //    MANDATORY propagation; PDF or unknown file types are no-ops.
        //    Free DOC / DOCX → exactly one FULL artifact.
        //    (Paid DOC / DOCX is NOT routed through this free flow; it
        //    goes through TransactionalPaidDocumentBinderImpl which passes
        //    paid=true so the factory also creates the LIMITED artifact.)
        artifactFactory.bootstrapInsideTransaction(primaryFile, false);

        // 6. Map response. The 3-argument overload forces pricingLocked=false
        //    which is correct for a freshly created document (no SUCCESS
        //    payment yet).
        return mapToDocumentCardDto(savedDocument, currentUser, primaryFile);
    }

    @Override
    public DocumentFile buildPrimaryDocumentFile(
            Document document,
            String storagePath,
            String fileUrl,
            String originalFileName,
            Long sizeBytes) {

        if (!StringUtils.hasText(storagePath)) {
            throw new IllegalArgumentException("storagePath must not be blank");
        }
        String resolvedBucket = supabaseProperties.getPublicDocumentBucket();
        if (resolvedBucket == null || resolvedBucket.isBlank()) {
            throw new IllegalStateException(
                    "Public document bucket is not configured");
        }
        return DocumentFile.builder()
                .document(document)
                .storageBucket(resolvedBucket.trim())
                .storagePath(storagePath.trim())
                .fileUrl(fileUrl)
                .originalFileName(originalFileName)
                .fileExtension(extractFileExtension(originalFileName))
                .sizeBytes(sizeBytes != null ? sizeBytes : 0L)
                .primary(true)
                .build();
    }

    /**
     * PAID-shape {@link DocumentFile} builder. Distinct from
     * {@link #buildPrimaryDocumentFile} so the two flows cannot
     * accidentally share invariants.
     *
     * <p>The PAID builder enforces:
     * <ul>
     *   <li>{@code fileUrl = null} — paid files MUST NOT carry a
     *       public URL on the row.</li>
     *   <li>{@code storageBucket} non-blank.</li>
     *   <li>{@code storagePath} non-blank.</li>
     *   <li>{@code mimeType} non-blank.</li>
     *   <li>{@code sizeBytes} non-null.</li>
     *   <li>{@code primary = true}.</li>
     * </ul>
     *
     * <p>All authoritative values come from the verified
     * {@link com.cmcu.itstudy.entity.PendingStorageUpload} +
     * {@link com.cmcu.itstudy.dto.storage.StorageObjectInfo} — never
     * from the request DTO. The binder passes these values through
     * unchanged.
     */
    @Override
    public DocumentFile buildPaidDocumentFile(
            Document document,
            String storageBucket,
            String storagePath,
            String mimeType,
            String originalFileName,
            Long sizeBytes) {

        if (document == null) {
            throw new IllegalArgumentException("document must not be null");
        }
        if (!StringUtils.hasText(storageBucket)) {
            throw new IllegalArgumentException("storageBucket must not be blank");
        }
        if (!StringUtils.hasText(storagePath)) {
            throw new IllegalArgumentException("storagePath must not be blank");
        }
        if (!StringUtils.hasText(mimeType)) {
            throw new IllegalArgumentException("mimeType must not be blank");
        }
        if (sizeBytes == null) {
            throw new IllegalArgumentException("sizeBytes must not be null");
        }
        return DocumentFile.builder()
                .document(document)
                .storageBucket(storageBucket)
                .storagePath(storagePath.trim())
                .fileUrl(null)
                .mimeType(mimeType)
                .originalFileName(originalFileName)
                .fileExtension(extractFileExtension(originalFileName))
                .sizeBytes(sizeBytes)
                .primary(true)
                .build();
    }

    private static FileType detectFileType(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return FileType.OTHER;
        }
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return FileType.PDF;
        }
        if (lower.endsWith(".doc") || lower.endsWith(".docx")) {
            return FileType.DOC;
        }
        if (lower.endsWith(".ppt") || lower.endsWith(".pptx")) {
            return FileType.PPT;
        }
        return FileType.OTHER;
    }

    private static long resolveDocumentPrice(boolean isPaid, Long requestedPrice) {
        if (!isPaid) {
            return 0L;
        }
        long v = requestedPrice == null ? 0L : requestedPrice;
        return v < 0L ? 0L : v;
    }

    private static String extractFileExtension(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "dat";
        }
        int i = fileName.lastIndexOf('.');
        if (i < 0 || i == fileName.length() - 1) {
            return "dat";
        }
        return fileName.substring(i + 1).toLowerCase();
    }

    /**
     * CREATE-RESPONSE mapper. {@code pricingLocked} is {@code false}
     * because a freshly created document cannot have a SUCCESS payment
     * yet; this matches the 3-argument overload in
     * {@code DocumentServiceImpl#mapToDocumentCardDto(...)}.
     */
    private DocumentCardDto mapToDocumentCardDto(
            Document document,
            User currentUser,
            DocumentFile primaryFile) {

        return DocumentCardDto.builder()
                .id(document.getId().toString())
                .title(document.getTitle())
                .slug(document.getSlug())
                .description(document.getDescription())
                .thumbnailUrl(document.getThumbnailUrl())
                .fileName(document.getFileName())
                .fileType(document.getFileType() != null ? document.getFileType().name() : "OTHER")
                .fileSize(document.getFileSize())
                .status(document.getStatus())
                .uploadDate(document.getCreatedAt())
                .views(document.getViewCount())
                .downloads(document.getDownloadCount())
                .bookmarks(document.getBookmarkCount())
                .categoryName(document.getCategory() != null
                        ? document.getCategory().getName() : null)
                .authorName(currentUser != null ? currentUser.getFullName() : null)
                .documentUrl(document.getFileUrl())
                .storagePath(primaryFile != null ? primaryFile.getStoragePath() : null)
                .isPaid(Boolean.TRUE.equals(document.getIsPaid()))
                .price(resolveDocumentPrice(
                        Boolean.TRUE.equals(document.getIsPaid()),
                        document.getPrice()))
                .pricingLocked(false)
                .build();
    }
}
