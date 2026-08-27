package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.dto.document.DocumentCardDto;
import com.cmcu.itstudy.dto.document.DocumentCreateRequestDto;
import com.cmcu.itstudy.dto.document.DocumentUpdateRequestDto;
import com.cmcu.itstudy.dto.document.MyDocumentAutoQuizCreateRequestDto;
import com.cmcu.itstudy.dto.document.MyDocumentAutoQuizDto;
import com.cmcu.itstudy.dto.document.MyDocumentDetailDto;
import com.cmcu.itstudy.dto.document.MyDocumentQuizItemDto;
import com.cmcu.itstudy.dto.document.MyDocumentQuizListDto;
import com.cmcu.itstudy.entity.*;
import com.cmcu.itstudy.enums.DocumentStatus;
import com.cmcu.itstudy.enums.FileType;
import com.cmcu.itstudy.enums.PaymentStatus;
import com.cmcu.itstudy.handle.DocumentPricingLockedException;
import com.cmcu.itstudy.repository.*;
import com.cmcu.itstudy.service.contract.DocumentAccessService;
import com.cmcu.itstudy.service.contract.DocumentService;
import com.cmcu.itstudy.service.contract.QuizGenerationService;
import com.cmcu.itstudy.service.contract.TransactionalDocumentCrudService;
import com.cmcu.itstudy.util.SlugUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentTagRepository documentTagRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final DocumentFileRepository documentFileRepository;
    private final PaymentRepository paymentRepository;
    private final DocumentReportRepository documentReportRepository;
    private final TransactionalDocumentCrudService transactionalDocumentCrudService;
    private final QuizGenerationService quizGenerationService;
    private final DocumentQuizRepository documentQuizRepository;
    private final QuizGenerationRepository quizGenerationRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final DocumentAccessService documentAccessService;
    private final DocumentPreviewArtifactRepository documentPreviewArtifactRepository;

    public DocumentServiceImpl(DocumentRepository documentRepository,
                               DocumentTagRepository documentTagRepository,
                               CategoryRepository categoryRepository,
                               TagRepository tagRepository,
                               DocumentFileRepository documentFileRepository,
                               PaymentRepository paymentRepository,
                               DocumentReportRepository documentReportRepository,
                               TransactionalDocumentCrudService transactionalDocumentCrudService,
                               QuizGenerationService quizGenerationService,
                               DocumentQuizRepository documentQuizRepository,
                               QuizGenerationRepository quizGenerationRepository,
                               QuizQuestionRepository quizQuestionRepository,
                               DocumentAccessService documentAccessService,
                               DocumentPreviewArtifactRepository documentPreviewArtifactRepository) {
        this.documentRepository = documentRepository;
        this.documentTagRepository = documentTagRepository;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.documentFileRepository = documentFileRepository;
        this.paymentRepository = paymentRepository;
        this.documentReportRepository = documentReportRepository;
        this.transactionalDocumentCrudService = transactionalDocumentCrudService;
        this.quizGenerationService = quizGenerationService;
        this.documentQuizRepository = documentQuizRepository;
        this.quizGenerationRepository = quizGenerationRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.documentAccessService = documentAccessService;
        this.documentPreviewArtifactRepository = documentPreviewArtifactRepository;
    }

    @Transactional(readOnly = true)
    @Override
    public Document getById(UUID id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Document not found with id: " + id));
    }

    @Transactional(readOnly = true)
    @Override
    public List<Document> getRelatedDocuments(UUID documentId, int limit) {
        Document doc = getById(documentId);
        if (doc.getCategory() == null || doc.getCategory().getId() == null) {
            return Collections.emptyList();
        }
        Slice<Document> slice = documentRepository.findRelatedDocumentsForDetail(
                DocumentStatus.APPROVED,
                doc.getCategory().getId(),
                documentId,
                PageRequest.of(0, Math.max(1, limit)));
        return slice.getContent();
    }

    @Override
    @Transactional
    public DocumentCardDto createDocument(DocumentCreateRequestDto documentCreateRequestDto, User currentUser) {
        // Phase C1: fail-closed legacy entry point.
        //
        // The legacy public method is kept only for backward-compatible
        // free-only callers. The paid create flow MUST go through
        // {@link com.cmcu.itstudy.service.contract.DocumentCommandRouter},
        // which composes the non-transactional
        // {@link com.cmcu.itstudy.service.contract.PaidDocumentUploadOrchestrator}
        // and the transactional binder in the correct order.
        //
        // This guard prevents a paid request from being routed into the
        // free transactional path, where it would skip the
        // Supabase object-info verification AND the atomic pending bind,
        // AND the row would be marked APPROVED-shaped without going
        // through the binder. The router already rejects this at the
        // HTTP edge, but defense-in-depth here protects any internal
        // caller that still uses this entry point.
        if (documentCreateRequestDto == null) {
            throw new IllegalArgumentException("documentCreateRequestDto must not be null");
        }
        if (Boolean.TRUE.equals(documentCreateRequestDto.getIsPaid())
                || documentCreateRequestDto.getUploadId() != null) {
            throw new IllegalArgumentException(
                    "Paid create must be routed through DocumentCommandRouter; "
                            + "this legacy entry point only accepts free-shape requests");
        }
        // Free branch — unchanged. Delegates into the dedicated free
        // transactional service so the controller and the router share
        // exactly one free-create code path.
        return transactionalDocumentCrudService.createFreeDocument(
                documentCreateRequestDto, currentUser);
    }

    @Override
    @Transactional
    public DocumentCardDto updateDocument(UUID documentId, DocumentUpdateRequestDto documentUpdateRequestDto, User currentUser) {
        Document existingDocument = getById(documentId);

        // Check if current user is the owner (or has permission to edit)
        // For now, assume user has permission if they are authenticated.
        // A more robust check would involve checking existingDocument.getCreatedBy().getId()
        if (!existingDocument.getCreatedBy().getId().equals(currentUser.getId())) {
            throw new SecurityException("User does not have permission to update this document.");
        }

        // ─────────────────────────────────────────────────────────────────
        // Pricing-change guard (legacy compatibility).
        //
        // The update DTO no longer enforces the 3,000 VND floor at the
        // validator level so legacy documents priced below 3,000 VND under
        // the previous 2,222 VND minimum can still round-trip metadata-only
        // edits. Here we compare the existing vs requested pricing and:
        //
        //   • reject a Paid doc whose stored price is null / non-positive
        //     (those are malformed, not legacy);
        //   • reject any paid request whose price has actually changed and
        //     is now below the minimum;
        //   • otherwise let the existing or requested price flow through.
        // ─────────────────────────────────────────────────────────────────
        boolean existingIsPaid = Boolean.TRUE.equals(existingDocument.getIsPaid());
        Long existingStoredPrice = existingDocument.getPrice();
        if (existingIsPaid && (existingStoredPrice == null || existingStoredPrice <= 0L)) {
            throw new IllegalArgumentException("Dữ liệu giá hiện tại của tài liệu không hợp lệ.");
        }
        long existingPrice = existingIsPaid ? existingStoredPrice : 0L;
        boolean requestedIsPaid = Boolean.TRUE.equals(documentUpdateRequestDto.getIsPaid());
        long requestedPrice = requestedIsPaid
                ? (documentUpdateRequestDto.getPrice() == null ? 0L : documentUpdateRequestDto.getPrice())
                : 0L;
        boolean pricingChanged =
                existingIsPaid != requestedIsPaid
                        || existingPrice != requestedPrice;

        // ─────────────────────────────────────────────────────────────────
        // Pricing-lock state (Phase C.1B2).
        //
        // For an EXISTING document we always need the real lock state because
        // the update response ({@link DocumentCardDto#pricingLocked}) must
        // reflect reality, not a default. The lock guard then runs only when
        // the request actually changes pricing; metadata-only updates on a
        // locked document still pass and return pricingLocked = true.
        //
        // The single repository call below is reused for BOTH:
        //   • the guard (pricingChanged && pricingLocked → throw 409)
        //   • the response mapper (pricingLocked propagates to the DTO)
        // so an update never issues two pricing-lock queries.
        //
        // Self-purchase rows are excluded by the repository method via
        // userId <> owner, defending against any historical / anomalous
        // data the create-payment guard did not catch.
        // ─────────────────────────────────────────────────────────────────
        UUID ownerId = existingDocument.getCreatedBy() != null
                ? existingDocument.getCreatedBy().getId()
                : null;
        boolean pricingLocked = ownerId != null
                && paymentRepository.existsByDocumentIdAndStatusAndUserIdNot(
                        documentId,
                        PaymentStatus.SUCCESS,
                        ownerId);

        if (pricingChanged && pricingLocked) {
            throw new DocumentPricingLockedException(
                    "Tài liệu đã có người mua nên không thể thay đổi hình thức hoặc giá bán."
            );
        }

        if (pricingChanged
                && requestedIsPaid
                && requestedPrice < DocumentUpdateRequestDto.MIN_PAID_DOCUMENT_PRICE) {
            throw new IllegalArgumentException(
                    "Giá bán tài liệu có phí phải từ 3.000 VND trở lên."
            );
        }

        // 1. Find or create Category
        Category category = categoryRepository.findByName(documentUpdateRequestDto.getCategory())
                .orElseThrow(() -> new NoSuchElementException("Category not found: " + documentUpdateRequestDto.getCategory()));

        // 2. Update Document entity fields
        existingDocument.setTitle(documentUpdateRequestDto.getTitle());
        existingDocument.setDescription(documentUpdateRequestDto.getDescription());
        // Slug regeneration policy on update:
        //   • Title unchanged → keep the current slug verbatim so public URLs
        //     and any external references remain stable. A metadata-only
        //     round-trip must NOT rewrite slug = base to slug = base-2.
        //   • Title changed  → derive a fresh unique slug from the new title,
        //     excluding THIS document id from the existence check so the row
        //     does not collide with its own previous slug (relevant when a
        //     suffix was previously added and the user is now re-editing).
        //     Soft-deleted rows still occupy their slug, so the same
        //     suffix-chain rule applies as on create.
        if (!existingDocument.getTitle().equals(documentUpdateRequestDto.getTitle())) {
            existingDocument.setSlug(SlugUtils.resolveUniqueSlug(
                    documentUpdateRequestDto.getTitle(),
                    candidate -> documentRepository.existsBySlugAndIdNot(candidate, existingDocument.getId())));
        }

        // ─────────────────────────────────────────────────────────────────
        // Phase 7B.6A — asset preservation on metadata-only updates.
        //
        // The update DTO now allows null/blank values for thumbnailUrl,
        // documentUrl, fileName, and fileSizeBytes. The previous behaviour
        // unconditionally overwrote the persisted columns with whatever
        // the FE sent (often ""), which silently erased metadata-only
        // edits. We now treat null/blank as "no replacement":
        //
        //   • thumbnailUrl null/blank  ⇒ keep current value, INCLUDING null
        //     when the document has never had a cover.
        //   • documentUrl  null/blank  ⇒ keep current value.
        //   • fileName     null/blank  ⇒ keep current value.
        //   • fileSizeBytes null       ⇒ keep current value. A non-null but
        //     zero/negative value is treated as a metadata round-trip
        //     placeholder and is also preserved.
        //
        // If the request carries a non-blank storagePath it ALWAYS signals
        // a real file replacement; in that case the caller is responsible
        // for sending the matching documentUrl/fileName/fileSizeBytes. The
        // detailed DocumentFile sync below follows the same rule.
        //
        // We deliberately do NOT interpret null as "delete cover" — there
        // is no remove-cover product action.
        // ─────────────────────────────────────────────────────────────────
        if (StringUtils.hasText(documentUpdateRequestDto.getThumbnailUrl())) {
            existingDocument.setThumbnailUrl(documentUpdateRequestDto.getThumbnailUrl().trim());
        }
        boolean fileReplacementRequested =
                StringUtils.hasText(documentUpdateRequestDto.getStoragePath());
        if (fileReplacementRequested) {
            // Caller is uploading a replacement file. All file-shaped fields
            // must come from the request verbatim.
            if (StringUtils.hasText(documentUpdateRequestDto.getDocumentUrl())) {
                existingDocument.setFileUrl(documentUpdateRequestDto.getDocumentUrl().trim());
            }
            if (StringUtils.hasText(documentUpdateRequestDto.getFileName())) {
                existingDocument.setFileName(documentUpdateRequestDto.getFileName());
            }
            if (documentUpdateRequestDto.getFileSizeBytes() != null
                    && documentUpdateRequestDto.getFileSizeBytes() >= 0L) {
                existingDocument.setFileSize(documentUpdateRequestDto.getFileSizeBytes());
            }
        } else {
            // Metadata-only edit (or replacement without a storagePath, which
            // we treat as no-op on file metadata). Preserve the existing
            // document file metadata so an empty/zero round-trip cannot wipe
            // the file.
            if (StringUtils.hasText(documentUpdateRequestDto.getDocumentUrl())) {
                existingDocument.setFileUrl(documentUpdateRequestDto.getDocumentUrl().trim());
            }
            if (StringUtils.hasText(documentUpdateRequestDto.getFileName())) {
                existingDocument.setFileName(documentUpdateRequestDto.getFileName());
            }
            if (documentUpdateRequestDto.getFileSizeBytes() != null
                    && documentUpdateRequestDto.getFileSizeBytes() > 0L) {
                existingDocument.setFileSize(documentUpdateRequestDto.getFileSizeBytes());
            }
        }
        existingDocument.setCategory(category); // Link to updated Category
        existingDocument.setUpdatedBy(currentUser); // Set updater
        boolean finalIsPaid = Boolean.TRUE.equals(documentUpdateRequestDto.getIsPaid());
        existingDocument.setIsPaid(finalIsPaid);
        existingDocument.setPrice(resolveDocumentPrice(
                finalIsPaid,
                documentUpdateRequestDto.getPrice() != null
                        ? documentUpdateRequestDto.getPrice()
                        : existingDocument.getPrice()));

        // Update file type if file name changed
        String fileName = existingDocument.getFileName();
        if (fileName != null && !fileName.isEmpty()) {
            String lowerCaseFileName = fileName.toLowerCase();
            if (lowerCaseFileName.endsWith(".pdf")) {
                existingDocument.setFileType(FileType.PDF);
            } else if (lowerCaseFileName.endsWith(".doc") || lowerCaseFileName.endsWith(".docx")) {
                existingDocument.setFileType(FileType.DOC);
            } else if (lowerCaseFileName.endsWith(".ppt") || lowerCaseFileName.endsWith(".pptx")) {
                existingDocument.setFileType(FileType.PPT);
            } else {
                existingDocument.setFileType(FileType.OTHER);
            }
        } else {
             existingDocument.setFileType(FileType.OTHER);
        }


        // 3. Handle Tags and DocumentTag associations
        // Remove existing tags for this document
        documentTagRepository.deleteAllByDocumentId(existingDocument.getId()); // Need to add this method to repo

        // Add new tags
        Set<DocumentTag> newDocumentTags = new HashSet<>();
        for (String tagName : documentUpdateRequestDto.getTags()) {
            String tagSlug = SlugUtils.resolveSlug(tagName, tagName);
            Tag tag = tagRepository.findBySlug(tagSlug)
                    .orElseGet(() -> { // Create tag if not exists
                        Tag newTag = Tag.builder()
                                .name(tagName)
                                .slug(tagSlug)
                                .build();
                        return tagRepository.save(newTag);
                    });

            DocumentTag documentTag = DocumentTag.builder()
                    .documentId(existingDocument.getId())
                    .tagId(tag.getId())
                    .document(existingDocument) // Set back-reference
                    .tag(tag) // Set back-reference
                    .createdAt(LocalDateTime.now())
                    .build();
            newDocumentTags.add(documentTag);
        }
        // Persist new associations
        documentTagRepository.saveAll(newDocumentTags);
        existingDocument.setDocumentTags(newDocumentTags); // Update set on document entity

        // 4. Save updated document
        Document updatedDocument = documentRepository.save(existingDocument);

        syncPrimaryDocumentFile(updatedDocument, documentUpdateRequestDto);

        DocumentFile primaryFile = documentFileRepository.findByDocumentIdAndPrimaryTrue(updatedDocument.getId())
                .orElse(null);

        // pricingLocked here is the REAL value queried above. Do NOT delegate
        // to the default overload because that one is reserved for the create
        // response (a freshly created document cannot have a SUCCESS payment
        // yet, so it is correctly forced to false there).
        return mapToDocumentCardDto(updatedDocument, currentUser, primaryFile, pricingLocked);
    }

    @Override
    @Transactional
    public void deleteDocument(UUID documentId, User currentUser) {
        Document document = getById(documentId);

        // Check ownership before deletion
        if (!document.getCreatedBy().getId().equals(currentUser.getId())) {
            throw new SecurityException("User does not have permission to delete this document.");
        }

        // Perform soft delete. Sample a single 'now' so the document
        // row and the quiz-generation cancel below stay consistent.
        LocalDateTime now = LocalDateTime.now();
        document.setDeleted(true);
        document.setDeletedAt(now);
        document.setDeletedBy(currentUser);
        documentRepository.save(document);

        // Phase QUIZ-AI-2B: cancel any active quiz-generation row
        // attached to this document inside the same transaction.
        // No network call; the cancel is purely a status flip.
        quizGenerationService.cancelForDocument(documentId, now);
    }

    @Transactional(readOnly = true)
    @Override
    public List<DocumentCardDto> getMyDocuments(User currentUser) {
        // Fetch documents created by the current user, not deleted, ordered by creation date
        List<Document> documents = documentRepository.findByCreatedByAndDeletedFalseOrderByCreatedAtDesc(currentUser);

        // Resolve the pricing-locked document ids in a SINGLE bulk query so the
        // owner list never falls into an N+1 pattern against PaymentRepository.
        Set<UUID> lockedDocumentIds = resolveLockedDocumentIds(documents);

        // Map to card DTO for consistency with service contract
        return documents.stream()
                .map(doc -> mapToDocumentCardDto(
                        doc,
                        currentUser,
                        documentFileRepository.findByDocumentIdAndPrimaryTrue(doc.getId()).orElse(null),
                        lockedDocumentIds.contains(doc.getId())))
                .collect(Collectors.toList());
    }

    /**
     * Returns the set of document ids (within the supplied {@code documents}
     * window) that already have at least one SUCCESS payment from a non-owner
     * buyer. Empty when the input is empty — no SQL is issued in that case.
     */
    private Set<UUID> resolveLockedDocumentIds(Collection<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return Collections.emptySet();
        }
        List<UUID> ids = documents.stream()
                .map(Document::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return Collections.emptySet();
        }
        List<UUID> locked = paymentRepository.findDistinctDocumentIdsWithSuccessfulBuyer(
                ids,
                PaymentStatus.SUCCESS,
                // Owner-self exclusion handled server-side too; the current
                // user IS the owner for every row in this list, so any
                // payment counted here was made by a different buyer.
                currentUserIdOrUnknown(documents));
        return locked == null ? Collections.emptySet() : new HashSet<>(locked);
    }

    /**
     * Resolves the user id to exclude from the bulk query. All documents in
     * the owner list share the same creator (the current user), so we only
     * need one id. When the creator is unexpectedly null on every row we
     * fall back to a UUID that matches nothing so the query still executes
     * safely — in practice the caller never reaches this branch because
     * documents without a creator would not pass the owner check.
     */
    private static UUID currentUserIdOrUnknown(Collection<Document> documents) {
        for (Document d : documents) {
            if (d != null && d.getCreatedBy() != null && d.getCreatedBy().getId() != null) {
                return d.getCreatedBy().getId();
            }
        }
        // Sentinel that is guaranteed to not equal any real user id; keeps
        // the query runnable while excluding every payment from the count.
        return new UUID(0L, 0L);
    }

    @Override
    @Transactional(readOnly = true)
    public MyDocumentDetailDto getMyDocumentDetail(UUID documentId, User currentUser) {
        Document document = getById(documentId);
        if (Boolean.TRUE.equals(document.getDeleted())) {
            throw new NoSuchElementException("Document not found: " + documentId);
        }
        if (document.getCreatedBy() == null || !document.getCreatedBy().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You do not have access to this document");
        }
        List<String> tagNames = documentTagRepository.findByDocumentId(documentId).stream()
                .map(DocumentTag::getTag)
                .filter(Objects::nonNull)
                .map(Tag::getName)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
        String documentUrl = resolveOwnerPreviewUrl(document);

        UUID ownerId = document.getCreatedBy().getId();
        long successfulPurchaseCount = paymentRepository.countByDocumentIdAndStatusAndUserIdNot(
                document.getId(),
                PaymentStatus.SUCCESS,
                ownerId);
        boolean pricingLocked = successfulPurchaseCount > 0L;

        return MyDocumentDetailDto.builder()
                .id(document.getId().toString())
                .title(document.getTitle())
                .description(document.getDescription())
                .documentUrl(documentUrl)
                .thumbnailUrl(document.getThumbnailUrl())
                .fileName(document.getFileName())
                .fileType(document.getFileType() != null ? document.getFileType().name() : null)
                .fileSizeBytes(document.getFileSize())
                .categoryName(document.getCategory() != null ? document.getCategory().getName() : null)
                .tags(tagNames)
                .status(document.getStatus())
                .rejectReason(document.getRejectReason())
                .createdAt(document.getCreatedAt())
                .isPaid(Boolean.TRUE.equals(document.getIsPaid()))
                .price(resolveOwnerPriceForResponse(document.getIsPaid(), document.getPrice()))
                .pricingLocked(pricingLocked)
                .successfulPurchaseCount(successfulPurchaseCount)
                .build();
    }

    private String resolveOwnerPreviewUrl(Document document) {
        Optional<DocumentFile> opt = documentFileRepository.findByDocumentIdAndPrimaryTrue(document.getId());
        if (opt.isPresent()) {
            DocumentFile f = opt.get();
            if (StringUtils.hasText(f.getFileUrl()) && isHttpUrl(f.getFileUrl())) {
                return f.getFileUrl().trim();
            }
            if (StringUtils.hasText(f.getStoragePath()) && isHttpUrl(f.getStoragePath())) {
                return f.getStoragePath().trim();
            }
        }
        return StringUtils.hasText(document.getFileUrl()) ? document.getFileUrl().trim() : null;
    }

    /**
     * Owner-detail / owner-list pricing field: free documents always report
     * {@code price = 0L} so the frontend can render the badge without dealing
     * with a nullable number; paid documents report the stored value as-is.
     */
    private static long resolveOwnerPriceForResponse(Boolean isPaid, Long storedPrice) {
        if (!Boolean.TRUE.equals(isPaid)) {
            return 0L;
        }
        long v = storedPrice == null ? 0L : storedPrice;
        return v < 0L ? 0L : v;
    }

    private static boolean isHttpUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String v = value.trim().toLowerCase();
        return v.startsWith("https://") || v.startsWith("http://");
    }

    /**
     * Normalize the final price stored on a document so {@code isPaid} and
     * {@code price} stay consistent. Free documents always store 0; paid
     * documents store the request value verbatim. Negative inputs on a paid
     * document are clamped to 0 as defense-in-depth; the create DTO and the
     * update service guard should reject these earlier with HTTP 400.
     *
     * <p>This helper does NOT enforce the minimum — legacy prices below
     * {@code MIN_PAID_DOCUMENT_PRICE} are intentionally preserved when the
     * update path decides pricing has not changed. See the pricing-change
     * guard at the top of {@link #updateDocument(UUID, DocumentUpdateRequestDto, User)}.
     */
    private static long resolveDocumentPrice(boolean isPaid, Long requestedPrice) {
        if (!isPaid) {
            return 0L;
        }
        long v = requestedPrice == null ? 0L : requestedPrice;
        return v < 0L ? 0L : v;
    }

    /**
     * CREATE-RESPONSE default. A document that was just created cannot have
     * any SUCCESS purchase yet, so reporting {@code pricingLocked = false}
     * here is correct.
     *
     * <p>DO NOT call this overload for any other endpoint that returns an
     * existing document — the update response in particular must reflect
     * the real lock state queried from {@code PaymentRepository}. Call the
     * 4-argument overload instead.
     */
    private DocumentCardDto mapToDocumentCardDto(Document document, User currentUser, DocumentFile primaryFile) {
        return mapToDocumentCardDto(document, currentUser, primaryFile, false);
    }

    private DocumentCardDto mapToDocumentCardDto(Document document, User currentUser, DocumentFile primaryFile, boolean pricingLocked) {
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
                .categoryName(document.getCategory() != null ? document.getCategory().getName() : null)
                .authorName(currentUser != null ? currentUser.getFullName() : null)
                .documentUrl(document.getFileUrl())
                .storagePath(primaryFile != null ? primaryFile.getStoragePath() : null)
                .isPaid(Boolean.TRUE.equals(document.getIsPaid()))
                .price(resolveOwnerPriceForResponse(document.getIsPaid(), document.getPrice()))
                .pricingLocked(pricingLocked)
                .build();
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

    private DocumentFile buildPrimaryDocumentFile(
            Document document,
            String storagePath,
            String fileUrl,
            String originalFileName,
            Long sizeBytes
    ) {
        return DocumentFile.builder()
                .document(document)
                .storagePath(storagePath.trim())
                .fileUrl(fileUrl)
                .originalFileName(originalFileName)
                .fileExtension(extractFileExtension(originalFileName))
                .sizeBytes(sizeBytes != null ? sizeBytes : 0L)
                .primary(true)
                .build();
    }

    private void syncPrimaryDocumentFile(Document document, DocumentUpdateRequestDto dto) {
        Optional<DocumentFile> existing = documentFileRepository.findByDocumentIdAndPrimaryTrue(document.getId());
        if (existing.isPresent()) {
            DocumentFile df = existing.get();
            // Phase 7B.6A — preserve existing primary-file metadata when the
            // request does not carry a replacement storagePath. A metadata-only
            // PUT must not wipe the DocumentFile row's URL / filename / size /
            // extension simply because the FE round-trips blanks.
            //
            // Semantics:
            //   • dto.storagePath non-blank            ⇒ real replacement,
            //     overwrite every field the request supplies (a missing field
            //     here means the binder will fill it elsewhere).
            //   • dto.storagePath null/blank AND the request supplies a
            //     non-blank documentUrl/fileName/fileSizeBytes  ⇒ metadata
            //     round-trip of those values (caller refreshed the cached
            //     URL, etc.); update only the fields that are present.
            //   • everything blank                     ⇒ preserve all
            //     existing fields verbatim. Do NOT delete the row.
            boolean replacementRequested = StringUtils.hasText(dto.getStoragePath());
            if (replacementRequested) {
                df.setStoragePath(dto.getStoragePath().trim());
                if (StringUtils.hasText(dto.getDocumentUrl())) {
                    df.setFileUrl(dto.getDocumentUrl().trim());
                }
                if (StringUtils.hasText(dto.getFileName())) {
                    df.setOriginalFileName(dto.getFileName());
                    df.setFileExtension(extractFileExtension(dto.getFileName()));
                }
                if (dto.getFileSizeBytes() != null && dto.getFileSizeBytes() >= 0L) {
                    df.setSizeBytes(dto.getFileSizeBytes());
                }
            } else {
                if (StringUtils.hasText(dto.getDocumentUrl())) {
                    df.setFileUrl(dto.getDocumentUrl().trim());
                }
                if (StringUtils.hasText(dto.getFileName())) {
                    df.setOriginalFileName(dto.getFileName());
                    df.setFileExtension(extractFileExtension(dto.getFileName()));
                }
                if (dto.getFileSizeBytes() != null && dto.getFileSizeBytes() > 0L) {
                    df.setSizeBytes(dto.getFileSizeBytes());
                }
            }
            documentFileRepository.save(df);
            return;
        }
        if (StringUtils.hasText(dto.getStoragePath())) {
            documentFileRepository.save(buildPrimaryDocumentFile(
                    document,
                    dto.getStoragePath().trim(),
                    dto.getDocumentUrl(),
                    dto.getFileName(),
                    dto.getFileSizeBytes()));
        }
    }

    @Transactional
    @Override
    public void reportDocument(UUID documentId, User reporter, com.cmcu.itstudy.dto.document.DocumentReportRequestDto requestDto) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new NoSuchElementException("Tài liệu không tồn tại"));

        Optional<DocumentReport> existingOpt = documentReportRepository.findByDocumentIdAndReporterId(documentId, reporter.getId());
        if (existingOpt.isPresent()) {
            DocumentReport existingReport = existingOpt.get();
            String st = existingReport.getStatus() != null ? existingReport.getStatus().toUpperCase() : "";
            if ("PENDING".equals(st)) {
                throw new IllegalArgumentException("Bạn đã gửi báo cáo cho tài liệu này và đang chờ xử lý.");
            }

            // Nếu báo cáo trước đó đã được xử lý (RESOLVED) hoặc bỏ qua (DISMISSED), cập nhật lại để gửi báo cáo mới
            existingReport.setReasonCode(requestDto.getReasonCode());
            existingReport.setDetail(requestDto.getDetail() != null ? requestDto.getDetail().trim() : "");
            existingReport.setStatus("PENDING");
            existingReport.setCreatedAt(LocalDateTime.now());
            existingReport.setResolvedAt(null);
            existingReport.setResolvedBy(null);

            documentReportRepository.save(existingReport);
            return;
        }

        DocumentReport report = DocumentReport.builder()
                .document(document)
                .reporter(reporter)
                .reasonCode(requestDto.getReasonCode())
                .detail(requestDto.getDetail() != null ? requestDto.getDetail().trim() : "")
                .status("PENDING")
                .build();

        documentReportRepository.save(report);
    }

    @Transactional(readOnly = true)
    @Override
    public com.cmcu.itstudy.dto.document.DocumentReportPageResponseDto getReportedDocuments(
            String status,
            String search,
            LocalDateTime startDate,
            LocalDateTime endDate,
            int page,
            int size
    ) {
        int p = Math.max(0, page);
        int s = size < 1 ? 10 : Math.min(size, 100);
        PageRequest pageRequest = PageRequest.of(p, s);
        String st = (status != null && !status.isBlank()) ? status.toUpperCase() : null;
        String q = (search != null && !search.isBlank()) ? search.trim() : null;
        org.springframework.data.domain.Page<DocumentReport> reports =
                documentReportRepository.searchReports(st, q, startDate, endDate, pageRequest);

        java.util.List<com.cmcu.itstudy.dto.document.DocumentReportResponseDto> content = reports.getContent().stream().map(r -> {
            String docTitle = "Tài liệu không tồn tại";
            String docAuthorId = null;
            String docAuthorName = "Không xác định";
            String docAuthorAvatar = null;
            String docStatus = null;
            String docIdStr = null;
            long count = 0L;

            try {
                Document doc = r.getDocument();
                if (doc != null) {
                    docIdStr = doc.getId() != null ? doc.getId().toString() : null;
                    docTitle = doc.getTitle() != null ? doc.getTitle() : "Tài liệu không tiêu đề";
                    docStatus = doc.getStatus() != null ? doc.getStatus().name() : null;

                    User author = doc.getCreatedBy();
                    if (author != null) {
                        docAuthorId = author.getId() != null ? author.getId().toString() : null;
                        docAuthorName = author.getFullName() != null ? author.getFullName() : (author.getEmail() != null ? author.getEmail() : "Không xác định");
                        docAuthorAvatar = author.getAvatarUrl();
                    }

                    if (doc.getId() != null) {
                        count = documentReportRepository.countByDocumentId(doc.getId());
                    }
                }
            } catch (Exception ex) {
                log.warn("Failed to extract document/author details for report {}: {}", r.getId(), ex.getMessage());
            }

            String reporterIdStr = null;
            String reporterNameStr = "Không xác định";
            String reporterAvatarStr = null;

            try {
                User reporter = r.getReporter();
                if (reporter != null) {
                    reporterIdStr = reporter.getId() != null ? reporter.getId().toString() : null;
                    reporterNameStr = reporter.getFullName() != null ? reporter.getFullName() : (reporter.getEmail() != null ? reporter.getEmail() : "Người dùng");
                    reporterAvatarStr = reporter.getAvatarUrl();
                }
            } catch (Exception ex) {
                log.warn("Failed to extract reporter details for report {}: {}", r.getId(), ex.getMessage());
            }

            return com.cmcu.itstudy.dto.document.DocumentReportResponseDto.builder()
                    .id(r.getId() != null ? r.getId().toString() : null)
                    .documentId(docIdStr)
                    .documentTitle(docTitle)
                    .documentAuthorId(docAuthorId)
                    .documentAuthorName(docAuthorName)
                    .documentAuthorAvatar(docAuthorAvatar)
                    .reporterId(reporterIdStr)
                    .reporterName(reporterNameStr)
                    .reporterAvatar(reporterAvatarStr)
                    .reasonCode(r.getReasonCode())
                    .detail(r.getDetail())
                    .status(r.getStatus())
                    .reportCount(count)
                    .documentStatus(docStatus)
                    .createdAt(r.getCreatedAt())
                    .resolvedAt(r.getResolvedAt())
                    .build();
        }).collect(java.util.stream.Collectors.toList());

        return com.cmcu.itstudy.dto.document.DocumentReportPageResponseDto.builder()
                .content(content)
                .page(reports.getNumber())
                .size(reports.getSize())
                .totalElements(reports.getTotalElements())
                .totalPages(reports.getTotalPages())
                .pendingCount(documentReportRepository.countByStatus("PENDING"))
                .resolvedCount(documentReportRepository.countByStatus("RESOLVED"))
                .dismissedCount(documentReportRepository.countByStatus("DISMISSED"))
                .build();
    }

    @Transactional
    @Override
    public void resolveReport(UUID reportId, User resolver) {
        DocumentReport report = documentReportRepository.findById(reportId)
                .orElseThrow(() -> new NoSuchElementException("Báo cáo không tồn tại"));
        report.setStatus("RESOLVED");
        report.setResolvedAt(LocalDateTime.now());
        report.setResolvedBy(resolver);
        documentReportRepository.save(report);
    }

    @Transactional
    @Override
    public void dismissReport(UUID reportId, User resolver) {
        DocumentReport report = documentReportRepository.findById(reportId)
                .orElseThrow(() -> new NoSuchElementException("Báo cáo không tồn tại"));
        report.setStatus("DISMISSED");
        report.setResolvedAt(LocalDateTime.now());
        report.setResolvedBy(resolver);
        documentReportRepository.save(report);
    }

    @Transactional(readOnly = true)
    @Override
    public MyDocumentAutoQuizDto getMyDocumentAutoQuiz(UUID documentId, User currentUser) {
        Document document = getById(documentId);
        if (Boolean.TRUE.equals(document.getDeleted())) {
            throw new NoSuchElementException("Document not found: " + documentId);
        }
        if (document.getCreatedBy() == null || !document.getCreatedBy().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You do not have access to this document");
        }

        java.util.List<com.cmcu.itstudy.entity.QuizGeneration> generations =
                quizGenerationService.findAllByDocumentId(documentId);
        if (generations == null || generations.isEmpty()) {
            throw new NoSuchElementException("Auto quiz not found for this document");
        }
        // Phase Multi Auto Quiz 1: the singular owner endpoint still
        // returns a single generation snapshot. Pick the latest one
        // (repository orders by requestedAt DESC, createdAt DESC, id
        // DESC). Plural listings belong to a later phase.
        com.cmcu.itstudy.entity.QuizGeneration generation = generations.get(0);

        MyDocumentAutoQuizDto.QuizInfo quizInfo = null;
        com.cmcu.itstudy.entity.Quiz quizEntity = generation.getQuiz();
        if (quizEntity != null) {
            long totalQuestions = quizQuestionRepository.countByQuiz_Id(quizEntity.getId());
            quizInfo = MyDocumentAutoQuizDto.QuizInfo.builder()
                    .quizId(quizEntity.getId().toString())
                    .title(quizEntity.getTitle())
                    .description(quizEntity.getDescription())
                    .totalQuestions(totalQuestions)
                    .durationMinutes(quizEntity.getDurationMinutes())
                    .passScorePercent(quizEntity.getPassScorePercent())
                    .build();
        }

        return MyDocumentAutoQuizDto.builder()
                .documentId(documentId.toString())
                .generationId(generation.getId().toString())
                .status(generation.getStatus())
                .requestedQuestionCount(generation.getRequestedQuestionCount())
                .focusTopic(generation.getFocusTopic())
                .requestedAt(generation.getRequestedAt())
                .processingAt(generation.getProcessingAt())
                .readyAt(generation.getReadyAt())
                .failedAt(generation.getFailedAt())
                .cancelledAt(generation.getCancelledAt())
                .lastError(generation.getLastError())
                .attempts(generation.getAttempts())
                .quiz(quizInfo)
                .build();
    }

    // ------------------------------------------------------------------------
    // Phase Multi Auto Quiz 2 — owner-facing plural read & additional create
    // ------------------------------------------------------------------------

    @Transactional(readOnly = true)
    @Override
    public List<MyDocumentAutoQuizDto> getMyDocumentAutoQuizzes(
            UUID documentId,
            User currentUser) {
        // Owner check (identical to singular endpoint).
        Document document = getById(documentId);
        if (Boolean.TRUE.equals(document.getDeleted())) {
            throw new NoSuchElementException("Document not found: " + documentId);
        }
        if (document.getCreatedBy() == null
                || !document.getCreatedBy().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You do not have access to this document");
        }

        List<com.cmcu.itstudy.entity.QuizGeneration> generations =
                quizGenerationService.findAllByDocumentId(documentId);
        if (generations.isEmpty()) {
            return Collections.emptyList();
        }

        // Phase Multi Auto Quiz 2 — avoid N+1 question count:
        // collect quiz IDs from READY generations in one batch query.
        List<UUID> quizIds = generations.stream()
                .filter(g -> g.getQuiz() != null)
                .map(g -> g.getQuiz().getId())
                .collect(Collectors.toList());

        Map<UUID, Long> questionCountMap = new HashMap<>();
        if (!quizIds.isEmpty()) {
            List<Object[]> rows =
                    quizQuestionRepository.countQuestionsGroupedByQuizId(quizIds);
            for (Object[] row : rows) {
                questionCountMap.put((UUID) row[0], (Long) row[1]);
            }
        }

        return generations.stream()
                .map(gen -> {
                    Long qCount = null;
                    if (gen.getQuiz() != null) {
                        qCount = questionCountMap.getOrDefault(
                                gen.getQuiz().getId(), 0L);
                    }
                    return toMyDocumentAutoQuizDto(gen, qCount);
                })
                .collect(Collectors.toList());
    }

    @Transactional
    @Override
    public MyDocumentAutoQuizDto createMyDocumentAutoQuiz(
            UUID documentId,
            MyDocumentAutoQuizCreateRequestDto request,
            User currentUser) {
        // A. Verify owner / document existence / not-deleted.
        Document document = getById(documentId);
        if (Boolean.TRUE.equals(document.getDeleted())) {
            throw new NoSuchElementException("Document not found: " + documentId);
        }
        if (document.getCreatedBy() == null
                || !document.getCreatedBy().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You do not have access to this document");
        }

        // B. Find the primary DocumentFile.
        DocumentFile primaryFile = documentFileRepository
                .findByDocumentIdAndPrimaryTrue(documentId)
                .orElseThrow(() -> new IllegalStateException(
                        "Primary file not found for document: " + documentId));

        // C. Resolve and validate file type.
        String ext = primaryFile.getFileExtension();
        if (ext == null || ext.isBlank()) {
            throw new IllegalArgumentException(
                    QuizGenerationServiceImpl.UNSUPPORTED_AUTO_QUIZ_MESSAGE);
        }
        com.cmcu.itstudy.enums.AllowedDocumentFileType fileType =
                com.cmcu.itstudy.enums.AllowedDocumentFileType.fromExtension(ext)
                        .orElseThrow(() -> new IllegalArgumentException(
                                QuizGenerationServiceImpl.UNSUPPORTED_AUTO_QUIZ_MESSAGE));

        // D. Enqueue — each call creates a brand-new generation.
        LocalDateTime now = LocalDateTime.now();
        com.cmcu.itstudy.entity.QuizGeneration generation =
                quizGenerationService.enqueueForDocument(
                        documentId,
                        primaryFile.getId(),
                        fileType,
                        request.getRequestedQuestionCount(),
                        request.getFocusTopic(),
                        now);

        // E. For DOC / DOCX: if the FULL preview is already READY,
        //    promote the new generation to QUEUED so it is picked up
        //    by the scheduler without waiting for another preview event.
        if (fileType == com.cmcu.itstudy.enums.AllowedDocumentFileType.DOC
                || fileType == com.cmcu.itstudy.enums.AllowedDocumentFileType.DOCX) {
            var latestFullArtifact =
                    documentPreviewArtifactRepository
                            .findFirstByDocumentFileIdAndArtifactKindOrderByCreatedAtDescIdDesc(
                                    primaryFile.getId(),
                                    com.cmcu.itstudy.enums.DocumentPreviewArtifactKind.FULL);
            if (latestFullArtifact.isPresent()
                    && latestFullArtifact.get().getStatus()
                            == com.cmcu.itstudy.enums.DocumentPreviewArtifactStatus.READY) {
                quizGenerationService.queueWhenSourceReady(
                        documentId, primaryFile.getId(), now);
                // Reload the generation so the response reflects the
                // DB status after the atomic UPDATE (avoid stale
                // persistence-context snapshot).
                generation = quizGenerationRepository.findById(generation.getId())
                        .orElse(generation);
            }
        }

        // F. Map to DTO (question count is 0 / null at creation time).
        return toMyDocumentAutoQuizDto(generation, null);
    }

    /**
     * Map a {@link com.cmcu.itstudy.entity.QuizGeneration} to a
     * {@link MyDocumentAutoQuizDto}.
     *
     * <p>Uses {@link com.cmcu.itstudy.entity.QuizGeneration#getQuiz()} —
     * the quiz already attached to the generation entity — so there is
     * no extra query for the quiz itself.</p>
     *
     * @param generation      the source entity
     * @param questionCount   pre-fetched question count for the attached quiz,
     *                       or {@code null} if no quiz is attached
     */
    private MyDocumentAutoQuizDto toMyDocumentAutoQuizDto(
            com.cmcu.itstudy.entity.QuizGeneration generation,
            Long questionCount) {
        MyDocumentAutoQuizDto.QuizInfo quizInfo = null;
        Quiz quizEntity = generation.getQuiz();
        if (quizEntity != null) {
            quizInfo = MyDocumentAutoQuizDto.QuizInfo.builder()
                    .quizId(quizEntity.getId().toString())
                    .title(quizEntity.getTitle())
                    .description(quizEntity.getDescription())
                    .totalQuestions(
                            questionCount != null ? questionCount : 0L)
                    .durationMinutes(quizEntity.getDurationMinutes())
                    .passScorePercent(quizEntity.getPassScorePercent())
                    .build();
        }

        return MyDocumentAutoQuizDto.builder()
                .documentId(generation.getDocument().getId().toString())
                .generationId(generation.getId().toString())
                .status(generation.getStatus())
                .requestedQuestionCount(generation.getRequestedQuestionCount())
                .focusTopic(generation.getFocusTopic())
                .requestedAt(generation.getRequestedAt())
                .processingAt(generation.getProcessingAt())
                .readyAt(generation.getReadyAt())
                .failedAt(generation.getFailedAt())
                .cancelledAt(generation.getCancelledAt())
                .lastError(generation.getLastError())
                .attempts(generation.getAttempts())
                .quiz(quizInfo)
                .build();
    }

    @Transactional(readOnly = true)
    @Override
    public MyDocumentQuizListDto getMyDocumentQuizzes(int page, int size, User currentUser) {
        int safePage = Math.max(page, 0);
        int safeSize = size > 0 ? size : 10;
        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(safePage, safeSize,
                        org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Direction.ASC,
                                "sortOrder", "id"));

        org.springframework.data.domain.Page<com.cmcu.itstudy.entity.DocumentQuiz> linkPage =
                documentQuizRepository.findByOwnerIdWithQuizAndDocumentPaged(currentUser.getId(), pageable);

        if (linkPage.isEmpty()) {
            return MyDocumentQuizListDto.builder()
                    .items(Collections.emptyList())
                    .page(safePage)
                    .totalPages(0)
                    .totalItems(0)
                    .build();
        }

        List<UUID> quizIds = linkPage.getContent().stream()
                .map(dq -> dq.getQuiz() != null ? dq.getQuiz().getId() : null)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        java.util.Map<UUID, Long> questionCounts = new java.util.HashMap<>();
        if (!quizIds.isEmpty()) {
            List<Object[]> countRows = quizQuestionRepository.countQuestionsGroupedByQuizId(quizIds);
            for (Object[] row : countRows) {
                if (row != null && row.length >= 2) {
                    UUID qId = row[0] instanceof UUID ? (UUID) row[0] : null;
                    Long cnt = row[1] instanceof Long ? (Long) row[1] : null;
                    if (qId != null && cnt != null) {
                        questionCounts.put(qId, cnt);
                    }
                }
            }
        }

        java.util.Map<UUID, com.cmcu.itstudy.entity.QuizGeneration> generationMap = new java.util.HashMap<>();
        if (!quizIds.isEmpty()) {
            List<com.cmcu.itstudy.entity.QuizGeneration> allGenerations =
                    quizGenerationRepository.findAllByQuiz_IdIn(quizIds);
            for (com.cmcu.itstudy.entity.QuizGeneration gen : allGenerations) {
                if (gen.getQuiz() != null && gen.getQuiz().getId() != null) {
                    generationMap.put(gen.getQuiz().getId(), gen);
                }
            }
        }

        List<MyDocumentQuizItemDto> pageItems = new java.util.ArrayList<>();
        for (com.cmcu.itstudy.entity.DocumentQuiz dq : linkPage.getContent()) {
            com.cmcu.itstudy.entity.Quiz quiz = dq.getQuiz();
            if (quiz == null) continue;
            com.cmcu.itstudy.entity.Document doc = dq.getDocument();
            if (doc == null) continue;

            com.cmcu.itstudy.entity.QuizGeneration qg = generationMap.get(quiz.getId());
            boolean isAuto = qg != null;

            pageItems.add(MyDocumentQuizItemDto.builder()
                    .quizId(quiz.getId().toString())
                    .quizTitle(quiz.getTitle())
                    .description(quiz.getDescription())
                    .totalQuestions(questionCounts.getOrDefault(quiz.getId(), 0L))
                    .durationMinutes(quiz.getDurationMinutes())
                    .passScorePercent(quiz.getPassScorePercent())
                    .isAutoGenerated(isAuto)
                    .generationId(qg != null ? qg.getId().toString() : null)
                    .generationStatus(qg != null && qg.getStatus() != null ? qg.getStatus().name() : null)
                    .documentId(doc.getId().toString())
                    .documentTitle(doc.getTitle())
                    .documentFileName(doc.getFileName())
                    .documentThumbnailUrl(doc.getThumbnailUrl())
                    .createdAt(quiz.getCreatedAt())
                    .build());
        }

        return MyDocumentQuizListDto.builder()
                .items(pageItems)
                .page(linkPage.getNumber())
                .totalPages(linkPage.getTotalPages())
                .totalItems(linkPage.getTotalElements())
                .build();
    }

}

