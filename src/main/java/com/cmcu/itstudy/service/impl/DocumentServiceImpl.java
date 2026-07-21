package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.dto.document.DocumentCardDto;
import com.cmcu.itstudy.dto.document.DocumentCreateRequestDto;
import com.cmcu.itstudy.dto.document.DocumentUpdateRequestDto;
import com.cmcu.itstudy.dto.document.MyDocumentDetailDto;
import com.cmcu.itstudy.entity.*;
import com.cmcu.itstudy.enums.DocumentStatus;
import com.cmcu.itstudy.enums.FileType;
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

    public DocumentServiceImpl(DocumentRepository documentRepository,
                               DocumentTagRepository documentTagRepository,
                               CategoryRepository categoryRepository,
                               TagRepository tagRepository,
                               DocumentFileRepository documentFileRepository) {
        this.documentRepository = documentRepository;
        this.documentTagRepository = documentTagRepository;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.documentFileRepository = documentFileRepository;
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

        return mapToDocumentCardDto(updatedDocument, currentUser, primaryFile);
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

        // Map to card DTO for consistency with service contract
        return documents.stream()
                .map(doc -> mapToDocumentCardDto(
                        doc,
                        currentUser,
                        documentFileRepository.findByDocumentIdAndPrimaryTrue(doc.getId()).orElse(null)))
                .collect(Collectors.toList());
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
     * documents store the request value (callers must have validated the
     * minimum via {@code DocumentCreateRequestDto#isPriceValid} /
     * {@code DocumentUpdateRequestDto#isPriceValid}). Negative or null inputs
     * on a paid document are coerced to {@code MIN_PAID_DOCUMENT_PRICE} as
     * defense-in-depth for legacy callers; validation should reject these
     * earlier with HTTP 400.
     */
    private static long resolveDocumentPrice(boolean isPaid, Long requestedPrice) {
        if (!isPaid) {
            return 0L;
        }
        long v = requestedPrice == null ? 0L : requestedPrice;
        return v < 0L ? 0L : v;
    }

    private DocumentCardDto mapToDocumentCardDto(Document document, User currentUser, DocumentFile primaryFile) {
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

}
