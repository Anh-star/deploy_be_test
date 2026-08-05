package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.dto.document.DocumentCardDto;
import com.cmcu.itstudy.dto.document.DocumentCreateRequestDto;
import com.cmcu.itstudy.dto.document.DocumentUpdateRequestDto;
import com.cmcu.itstudy.dto.document.MyDocumentDetailDto;
import com.cmcu.itstudy.entity.*;
import com.cmcu.itstudy.enums.DocumentStatus;
import com.cmcu.itstudy.enums.FileType;
import com.cmcu.itstudy.enums.PaymentStatus;
import com.cmcu.itstudy.handle.DocumentPricingLockedException;
import com.cmcu.itstudy.repository.*;
import com.cmcu.itstudy.service.contract.DocumentService;
import com.cmcu.itstudy.util.SlugUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentTagRepository documentTagRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final DocumentFileRepository documentFileRepository;
    private final PaymentRepository paymentRepository;
    private final DocumentReportRepository documentReportRepository;

    public DocumentServiceImpl(DocumentRepository documentRepository,
                               DocumentTagRepository documentTagRepository,
                               CategoryRepository categoryRepository,
                               TagRepository tagRepository,
                               DocumentFileRepository documentFileRepository,
                               PaymentRepository paymentRepository,
                               DocumentReportRepository documentReportRepository) {
        this.documentRepository = documentRepository;
        this.documentTagRepository = documentTagRepository;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.documentFileRepository = documentFileRepository;
        this.paymentRepository = paymentRepository;
        this.documentReportRepository = documentReportRepository;
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
        // 1. Find or create Category
        Category category = categoryRepository.findByName(documentCreateRequestDto.getCategory())
                .orElseThrow(() -> new NoSuchElementException("Category not found: " + documentCreateRequestDto.getCategory()));

        // 2. Create Document entity
        Document document = Document.builder()
                .title(documentCreateRequestDto.getTitle())
                .slug(SlugUtils.resolveSlug(documentCreateRequestDto.getTitle(), documentCreateRequestDto.getTitle())) // Generate slug from title
                .description(documentCreateRequestDto.getDescription())
                .fileUrl(documentCreateRequestDto.getDocumentUrl())
                .fileName(documentCreateRequestDto.getFileName())
                .fileSize(documentCreateRequestDto.getFileSizeBytes())
                .thumbnailUrl(documentCreateRequestDto.getThumbnailUrl())
                .category(category) // Link to Category
                .createdBy(currentUser) // Set creator
                .updatedBy(currentUser) // Set initial updater
                .status(DocumentStatus.PENDING) // Default status
                .viewCount(0L)
                .downloadCount(0L)
                .bookmarkCount(0L)
                .deleted(false)
                .isPaid(documentCreateRequestDto.getIsPaid())
                .price(resolveDocumentPrice(
                        Boolean.TRUE.equals(documentCreateRequestDto.getIsPaid()),
                        documentCreateRequestDto.getPrice()))
                .build();

        // Set file type based on extension or frontend hint (more robust to check extension from fileName)
        String fileName = document.getFileName();
        if (fileName != null && !fileName.isEmpty()) {
            String lowerCaseFileName = fileName.toLowerCase();
            if (lowerCaseFileName.endsWith(".pdf")) {
                document.setFileType(FileType.PDF);
            } else if (lowerCaseFileName.endsWith(".doc") || lowerCaseFileName.endsWith(".docx")) {
                document.setFileType(FileType.DOC);
            } else if (lowerCaseFileName.endsWith(".ppt") || lowerCaseFileName.endsWith(".pptx")) {
                document.setFileType(FileType.PPT);
            } else {
                document.setFileType(FileType.OTHER);
            }
        } else {
             document.setFileType(FileType.OTHER); // Default if no name
        }


        // 3. Save document to get ID and persist associations
        Document savedDocument = documentRepository.save(document);

        // 4. Handle Tags and DocumentTag associations
        Set<DocumentTag> documentTags = new HashSet<>();
        for (String tagName : documentCreateRequestDto.getTags()) {
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
                    .documentId(savedDocument.getId())
                    .tagId(tag.getId())
                    .document(savedDocument) // Set back-reference for entity graph loading
                    .tag(tag) // Set back-reference for entity graph loading
                    .createdAt(LocalDateTime.now())
                    .build();
            documentTags.add(documentTag);
        }
        savedDocument.setDocumentTags(documentTags); // Set associations
        // Note: DocumentTag will be saved via cascade or explicit save if needed. JPA typically handles this if configured.
        // For safety, we can explicitly save them if cascade is not set up correctly.
        documentTagRepository.saveAll(documentTags);

        DocumentFile primaryFile = documentFileRepository.save(buildPrimaryDocumentFile(
                savedDocument,
                documentCreateRequestDto.getStoragePath(),
                documentCreateRequestDto.getDocumentUrl(),
                documentCreateRequestDto.getFileName(),
                documentCreateRequestDto.getFileSizeBytes()
        ));

        return mapToDocumentCardDto(savedDocument, currentUser, primaryFile);
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
        // Only update slug if it's provided and different, or if title changed significantly.
        // For now, let's regenerate slug if title changes.
        if (!existingDocument.getTitle().equals(documentUpdateRequestDto.getTitle())) {
            existingDocument.setSlug(SlugUtils.resolveSlug(documentUpdateRequestDto.getTitle(), documentUpdateRequestDto.getTitle()));
        }
        existingDocument.setFileUrl(documentUpdateRequestDto.getDocumentUrl());
        existingDocument.setFileName(documentUpdateRequestDto.getFileName());
        existingDocument.setFileSize(documentUpdateRequestDto.getFileSizeBytes());
        existingDocument.setThumbnailUrl(documentUpdateRequestDto.getThumbnailUrl());
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

        // Perform soft delete
        document.setDeleted(true);
        document.setDeletedAt(LocalDateTime.now());
        document.setDeletedBy(currentUser);
        documentRepository.save(document);
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
            df.setFileUrl(dto.getDocumentUrl());
            df.setOriginalFileName(dto.getFileName());
            df.setFileExtension(extractFileExtension(dto.getFileName()));
            df.setSizeBytes(dto.getFileSizeBytes() != null ? dto.getFileSizeBytes() : 0L);
            if (StringUtils.hasText(dto.getStoragePath())) {
                df.setStoragePath(dto.getStoragePath().trim());
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

        if (documentReportRepository.existsByDocumentIdAndReporterId(documentId, reporter.getId())) {
            throw new IllegalArgumentException("Bạn đã báo cáo tài liệu này rồi");
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
    public org.springframework.data.domain.Page<com.cmcu.itstudy.dto.document.DocumentReportResponseDto> getReportedDocuments(String status, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Slice<DocumentReport> reportsSlice;
        org.springframework.data.domain.Page<DocumentReport> reports;

        if (StringUtils.hasText(status)) {
            reports = documentReportRepository.findByStatusAndDocumentDeletedFalse(status.toUpperCase(), pageRequest);
        } else {
            reports = documentReportRepository.findByDocumentDeletedFalse(pageRequest);
        }

        return reports.map(r -> {
            Document doc = r.getDocument();
            User reporter = r.getReporter();
            User author = doc != null ? doc.getCreatedBy() : null;
            long count = doc != null ? documentReportRepository.countByDocumentId(doc.getId()) : 0L;

            return com.cmcu.itstudy.dto.document.DocumentReportResponseDto.builder()
                    .id(r.getId() != null ? r.getId().toString() : null)
                    .documentId(doc != null ? doc.getId().toString() : null)
                    .documentTitle(doc != null ? doc.getTitle() : "Tài liệu không tồn tại")
                    .documentAuthorId(author != null ? author.getId().toString() : null)
                    .documentAuthorName(author != null ? author.getFullName() : "Không xác định")
                    .documentAuthorAvatar(author != null ? author.getAvatarUrl() : null)
                    .reporterId(reporter != null ? reporter.getId().toString() : null)
                    .reporterName(reporter != null ? reporter.getFullName() : "Không xác định")
                    .reporterAvatar(reporter != null ? reporter.getAvatarUrl() : null)
                    .reasonCode(r.getReasonCode())
                    .detail(r.getDetail())
                    .status(r.getStatus())
                    .reportCount(count)
                    .documentStatus(doc != null && doc.getStatus() != null ? doc.getStatus().name() : null)
                    .createdAt(r.getCreatedAt())
                    .resolvedAt(r.getResolvedAt())
                    .build();
        });
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

}

