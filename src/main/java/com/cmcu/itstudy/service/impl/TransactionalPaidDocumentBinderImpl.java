package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.dto.document.DocumentCardDto;
import com.cmcu.itstudy.dto.document.DocumentCreateRequestDto;
import com.cmcu.itstudy.dto.storage.StorageObjectInfo;
import com.cmcu.itstudy.entity.Category;
import com.cmcu.itstudy.entity.Document;
import com.cmcu.itstudy.entity.DocumentFile;
import com.cmcu.itstudy.entity.DocumentTag;
import com.cmcu.itstudy.entity.PendingStorageUpload;
import com.cmcu.itstudy.entity.Tag;
import com.cmcu.itstudy.entity.User;
import com.cmcu.itstudy.enums.AllowedDocumentFileType;
import com.cmcu.itstudy.enums.DocumentStatus;
import com.cmcu.itstudy.enums.PendingUploadStatus;
import com.cmcu.itstudy.handle.PendingUploadAlreadyBoundException;
import com.cmcu.itstudy.handle.PendingUploadBindConflictException;
import com.cmcu.itstudy.handle.PendingUploadExpiredException;
import com.cmcu.itstudy.handle.PendingUploadNotFoundException;
import com.cmcu.itstudy.handle.PendingUploadNotOwnedException;
import com.cmcu.itstudy.repository.CategoryRepository;
import com.cmcu.itstudy.repository.DocumentFileRepository;
import com.cmcu.itstudy.repository.DocumentRepository;
import com.cmcu.itstudy.repository.DocumentTagRepository;
import com.cmcu.itstudy.repository.PendingStorageUploadRepository;
import com.cmcu.itstudy.repository.TagRepository;
import com.cmcu.itstudy.repository.UserRepository;
import com.cmcu.itstudy.service.contract.QuizGenerationService;
import com.cmcu.itstudy.service.contract.TransactionalDocumentCrudService;
import com.cmcu.itstudy.service.contract.TransactionalPaidDocumentBinder;
import com.cmcu.itstudy.util.SlugUtils;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Default implementation of {@link TransactionalPaidDocumentBinder}.
 *
 * <p>This service runs inside the bind transaction propagated by
 * {@link com.cmcu.itstudy.service.contract.PaidDocumentUploadOrchestrator}
 * — the orchestrator's own bean is
 * {@code @Transactional(NOT_SUPPORTED)}, so the {@code REQUIRED}
 * propagation here starts a NEW transaction on entry.
 *
 * <p>Failure semantics inside this method (everything rolls back):
 * <ul>
 *   <li>missing / not-owned pending → 400/403, rollback</li>
 *   <li>expired / BOUND / CANCELED pending → 409, rollback</li>
 *   <li>bind UPDATE affected 0 rows (race) → 409, rollback</li>
 *   <li>size or MIME mismatch that somehow slipped here →
 *       409, rollback</li>
 * </ul>
 *
 * <p>The remote cleanup scheduler that the binder references
 * through {@link com.cmcu.itstudy.entity.StorageCleanupTask} is
 * intentionally NOT implemented in Phase C1; this binder never
 * enqueues anything itself. If a verify error reaches this method
 * (which by Phase-C1 design must NOT happen) the binder throws
 * instead of silently committing a wrong file.
 */
@Service
public class TransactionalPaidDocumentBinderImpl implements TransactionalPaidDocumentBinder {

    private final DocumentRepository documentRepository;
    private final DocumentTagRepository documentTagRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final DocumentFileRepository documentFileRepository;
    private final UserRepository userRepository;
    private final PendingStorageUploadRepository pendingUploadRepository;
    private final TransactionalDocumentCrudService transactionalDocumentCrudService;
    private final EntityManager entityManager;
    private final Clock clock;
    private final DocumentPreviewArtifactFactory artifactFactory;
    private final QuizGenerationService quizGenerationService;

    public TransactionalPaidDocumentBinderImpl(
            DocumentRepository documentRepository,
            DocumentTagRepository documentTagRepository,
            CategoryRepository categoryRepository,
            TagRepository tagRepository,
            DocumentFileRepository documentFileRepository,
            UserRepository userRepository,
            PendingStorageUploadRepository pendingUploadRepository,
            TransactionalDocumentCrudService transactionalDocumentCrudService,
            EntityManager entityManager,
            Clock clock,
            DocumentPreviewArtifactFactory artifactFactory,
            QuizGenerationService quizGenerationService) {
        this.documentRepository = documentRepository;
        this.documentTagRepository = documentTagRepository;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.documentFileRepository = documentFileRepository;
        this.userRepository = userRepository;
        this.pendingUploadRepository = pendingUploadRepository;
        this.transactionalDocumentCrudService = transactionalDocumentCrudService;
        this.entityManager = entityManager;
        this.clock = clock;
        this.artifactFactory = artifactFactory;
        this.quizGenerationService = quizGenerationService;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public DocumentCardDto bindPaidCreate(
            DocumentCreateRequestDto metadata,
            UUID uploadId,
            UUID currentUserId,
            StorageObjectInfo verified,
            LocalDateTime now) {

        if (metadata == null) {
            throw new IllegalArgumentException("metadata must not be null");
        }
        if (uploadId == null) {
            throw new IllegalArgumentException("uploadId must not be null");
        }
        if (currentUserId == null) {
            throw new IllegalArgumentException("currentUserId must not be null");
        }
        if (verified == null) {
            throw new IllegalArgumentException("verified must not be null");
        }
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }
        if (!Boolean.TRUE.equals(metadata.getIsPaid())) {
            throw new IllegalArgumentException(
                    "bindPaidCreate must only be called for paid documents");
        }
        if (metadata.getPrice() == null
                || metadata.getPrice() < DocumentCreateRequestDto.MIN_PAID_DOCUMENT_PRICE) {
            // Defense-in-depth: the DTO cross-field rule has already
            // enforced this; this only fires on a programming error.
            throw new IllegalArgumentException(
                    "Paid price below minimum");
        }

        // Sample the canonical "now" INSIDE the bind transaction so
        // a row that expired during the orchestrator's pre-bind
        // Supabase HTTP roundtrip is correctly rejected here. The
        // orchestrator-supplied 'now' may be stale by the time the
        // Supabase response lands, so it is used only as a fallback
        // (and for callers that do not have access to a clock).
        LocalDateTime bindNow = LocalDateTime.now(clock);

        // 1. Reload authenticated User. Throws NoSuchElementException
        //    (mapped to 404) if the user was deleted mid-flight.
        User owner = userRepository.findById(currentUserId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Authenticated user not found: " + currentUserId));

        // 2. Reload Pending. The query is by primary key so it always
        //    returns the row as it stands at the start of this
        //    transaction; the final bindPendingUpload(...) flip is
        //    race-protected by SQL Server UPDLOCK.
        PendingStorageUpload pending = pendingUploadRepository.findByUploadId(uploadId)
                .orElseThrow(() -> new PendingUploadNotFoundException(
                        "Pending upload not found"));

        // 3. Recheck ownership, status, expiry, bucket, path, size, MIME.
        //    Any failure here rolls everything back. Pending stays
        //    PENDING until the cleanup scheduler catches it; no
        //    remote delete is performed from this transaction.
        verifyPendingOrThrow(pending, owner, verified, bindNow, uploadId);

        // 4. Build the Document row from validated metadata.
        Category category = categoryRepository.findByName(metadata.getCategory())
                .orElseThrow(() -> new NoSuchElementException(
                        "Category not found: " + metadata.getCategory()));

        Document document = Document.builder()
                .title(metadata.getTitle())
                // Globally-unique slug resolver. Existence check is
                // delegated to a lambda so the binder stays free of any
                // direct repository knowledge of the slug uniqueness
                // rules. Soft-deleted rows still occupy their slug
                // (tbl_documents.slug is a plain UNIQUE), so a fresh
                // paid create with the same title gets a suffixed slug.
                .slug(SlugUtils.resolveUniqueSlug(
                        metadata.getTitle(),
                        documentRepository::existsBySlug))
                .description(metadata.getDescription())
                .fileUrl(null) // paid files never expose fileUrl
                .fileName(pending.getExpectedFileName())
                .fileSize(verified.sizeBytes())
                .thumbnailUrl(metadata.getThumbnailUrl())
                .category(category)
                .createdBy(owner)
                .updatedBy(owner)
                .status(DocumentStatus.PENDING)
                .viewCount(0L)
                .downloadCount(0L)
                .bookmarkCount(0L)
                .deleted(false)
                .isPaid(Boolean.TRUE)
                .price(metadata.getPrice())
                .build();

        document.setFileType(detectFileType(pending.getExpectedFileName()));

        Document savedDocument = documentRepository.save(document);

        // 5. Tags.
        Set<DocumentTag> documentTags = new HashSet<>();
        for (String tagName : metadata.getTags()) {
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
                    .createdAt(bindNow)
                    .build();
            documentTags.add(documentTag);
        }
        savedDocument.setDocumentTags(documentTags);
        documentTagRepository.saveAll(documentTags);

        // 6. Primary DocumentFile — private bucket row, no public URL.
        //    Use the dedicated PAID builder (not the free builder) so
        //    the paid invariants (bucket, mime, fileUrl=null, size)
        //    are enforced in one place. The free builder would only
        //    set size/original/extension and leave bucket + mime
        //    unset, forcing this method to re-assign them.
        DocumentFile primaryFile = transactionalDocumentCrudService.buildPaidDocumentFile(
                savedDocument,
                pending.getStorageBucket(),
                pending.getStoragePath(),
                pending.getExpectedMimeType(),
                pending.getExpectedFileName(),
                verified.sizeBytes());
        documentFileRepository.save(primaryFile);

        // 6.05. Phase QUIZ-AI-2B: persist the upload-time AI quiz intent
        //       as a tbl_quiz_generations row inside this same REQUIRED
        //       transaction. The boolean + integer pair was already
        //       validated by the DTO @AssertTrue and @Min/@Max. PDF →
        //       QUEUED; DOC/DOCX → WAITING_SOURCE; PPT/PPTX rejected.
        //       No n8n call, no signed URL, no scheduler.
        if (Boolean.TRUE.equals(metadata.getGenerateQuiz())) {
            AllowedDocumentFileType quizFileType = resolvePaidQuizFileType(
                    primaryFile, pending);
            quizGenerationService.enqueueForDocument(
                    savedDocument.getId(),
                    primaryFile.getId(),
                    quizFileType,
                    metadata.getQuizQuestionCount(),
                    metadata.getQuizFocusTopic(),
                    now);
        }

        // 6.1. Bootstrap a FRESH-Office preview artifact (DOC / DOCX only).
        //       The factory joins the binder's REQUIRED transaction via
        //       MANDATORY propagation; a later flush / bind / response
        //       failure rolls back the artifacts together with the
        //       Document and DocumentFile rows.
        //
        //       paid DOC / DOCX → TWO artifacts (FULL + LIMITED).
        //       Both rows share documentFileId, sourceChecksumSha256,
        //       and variantVersion = INITIAL_VARIANT_VERSION.
        artifactFactory.bootstrapInsideTransaction(primaryFile, true);

        // 7. Flush every pending JPA write before the native JDBC bind.
        //    This preserves the single transaction while ensuring SQL Server
        //    can resolve bound_document_id against tbl_documents immediately.
        entityManager.flush();

        // 8. Bind. The fragment method is @Transactional(MANDATORY)
        //    and verified-by-UPDATE inside SQL Server. Exactly 1 row
        //    must match; otherwise the request is racing another bind.
        //    bindNow is sampled INSIDE this transaction (not the
        //    orchestrator-supplied 'now'), so a row that expired
        //    mid-flight is not bound using a stale timestamp.
        UUID savedDocId = savedDocument.getId();
        boolean bound = pendingUploadRepository.bindPendingUpload(
                uploadId, owner.getId(), savedDocId, bindNow);
        if (!bound) {
            throw new PendingUploadBindConflictException(
                    "Pending upload could not be bound (race or expired)");
        }

        // 9. Build the response. Same shape as the free-create
        //    response so the controller stays unconditional.
        return DocumentCardDto.builder()
                .id(savedDocId.toString())
                .title(savedDocument.getTitle())
                .slug(savedDocument.getSlug())
                .description(savedDocument.getDescription())
                .thumbnailUrl(savedDocument.getThumbnailUrl())
                .fileName(savedDocument.getFileName())
                .fileType(savedDocument.getFileType() != null
                        ? savedDocument.getFileType().name() : "OTHER")
                .fileSize(savedDocument.getFileSize())
                .status(savedDocument.getStatus())
                .uploadDate(savedDocument.getCreatedAt())
                .views(savedDocument.getViewCount())
                .downloads(savedDocument.getDownloadCount())
                .bookmarks(savedDocument.getBookmarkCount())
                .categoryName(category.getName())
                .authorName(owner.getFullName())
                .documentUrl(null) // paid file: no URL persisted
                .storagePath(primaryFile.getStoragePath())
                .isPaid(Boolean.TRUE)
                .price(metadata.getPrice())
                .pricingLocked(false)
                .build();
    }

    private void verifyPendingOrThrow(
            PendingStorageUpload pending,
            User owner,
            StorageObjectInfo verified,
            LocalDateTime now,
            UUID uploadId) {

        UUID pendingOwnerId = pending.getUser() != null
                ? pending.getUser().getId()
                : null;
        if (pendingOwnerId == null || !Objects.equals(pendingOwnerId, owner.getId())) {
            // 403 — never echo uploadId in the message.
            throw new PendingUploadNotOwnedException(
                    "Pending upload does not belong to current user");
        }
        if (pending.getStatus() != PendingUploadStatus.PENDING) {
            // Already BOUND, CANCELED, CLEANING, EXPIRED → replay.
            throw new PendingUploadAlreadyBoundException(
                    "Pending upload is no longer bindable");
        }
        if (pending.getExpiresAt() == null || !pending.getExpiresAt().isAfter(now)) {
            throw new PendingUploadExpiredException(
                    "Pending upload bind deadline has passed");
        }
        if (pending.getExpectedSizeBytes() == null
                || verified.sizeBytes() != pending.getExpectedSizeBytes()) {
            // Defense-in-depth: should be unreachable after orchestrator
            // verification, but we re-check here so a malicious or
            // mistuned orchestrator cannot wedge wrong bytes into the DB.
            throw new PendingUploadBindConflictException(
                    "Verified size does not match pending upload expected size");
        }
        if (!StringUtils.hasText(pending.getExpectedMimeType())
                || verified.contentType() == null
                || !pending.getExpectedMimeType().equalsIgnoreCase(
                        verified.contentType())) {
            throw new PendingUploadBindConflictException(
                    "Verified content type does not match pending upload expected MIME type");
        }
    }

    private static com.cmcu.itstudy.enums.FileType detectFileType(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return com.cmcu.itstudy.enums.FileType.OTHER;
        }
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return com.cmcu.itstudy.enums.FileType.PDF;
        }
        if (lower.endsWith(".doc") || lower.endsWith(".docx")) {
            return com.cmcu.itstudy.enums.FileType.DOC;
        }
        if (lower.endsWith(".ppt") || lower.endsWith(".pptx")) {
            return com.cmcu.itstudy.enums.FileType.PPT;
        }
        return com.cmcu.itstudy.enums.FileType.OTHER;
    }

    /**
     * Resolves the {@link AllowedDocumentFileType} for the quiz-generation
     * enqueue on the paid path. The verified MIME is the authoritative
     * signal for paid uploads; the on-disk extension is a secondary
     * fallback when the extension column is already populated on the
     * freshly-built primary file.
     *
     * <p>Only PDF / DOC / DOCX are accepted for Auto Quiz; PPT / PPTX
     * (and any other type) throw {@link IllegalArgumentException} carrying
     * the shared Vietnamese message from
     * {@link QuizGenerationServiceImpl#UNSUPPORTED_AUTO_QUIZ_MESSAGE}. The
     * binder transaction rolls back cleanly. The exception is the
     * project's existing 400 mapping for an unsupported file type; no new
     * exception hierarchy is introduced in Phase 2B.
     */
    private static AllowedDocumentFileType resolvePaidQuizFileType(
            DocumentFile primaryFile,
            PendingStorageUpload pending) {
        java.util.Optional<AllowedDocumentFileType> fromMime =
                AllowedDocumentFileType.fromMimeType(pending.getExpectedMimeType());
        if (fromMime.isPresent()) {
            AllowedDocumentFileType t = fromMime.get();
            if (t == AllowedDocumentFileType.PPT
                    || t == AllowedDocumentFileType.PPTX) {
                throw new IllegalArgumentException(
                        QuizGenerationServiceImpl.UNSUPPORTED_AUTO_QUIZ_MESSAGE);
            }
            return t;
        }
        AllowedDocumentFileType fromExt =
                AllowedDocumentFileType.fromExtension(primaryFile.getFileExtension())
                        .orElseThrow(() -> new IllegalArgumentException(
                                QuizGenerationServiceImpl.UNSUPPORTED_AUTO_QUIZ_MESSAGE));
        if (fromExt == AllowedDocumentFileType.PPT
                || fromExt == AllowedDocumentFileType.PPTX) {
            throw new IllegalArgumentException(
                    QuizGenerationServiceImpl.UNSUPPORTED_AUTO_QUIZ_MESSAGE);
        }
        return fromExt;
    }
}
