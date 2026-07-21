package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.dto.document.DocumentCardResponseDto;
import com.cmcu.itstudy.dto.document.DocumentListRequestDto;
import com.cmcu.itstudy.dto.document.PagedResponseDocumentCardDto;
import com.cmcu.itstudy.entity.Document;
import com.cmcu.itstudy.entity.DocumentBookmark;
import com.cmcu.itstudy.entity.DocumentDownload;
import com.cmcu.itstudy.entity.DocumentTag;
import com.cmcu.itstudy.entity.DocumentView;
import com.cmcu.itstudy.entity.User;
import com.cmcu.itstudy.enums.DocumentStatus;
import com.cmcu.itstudy.repository.DocumentBookmarkRepository;
import com.cmcu.itstudy.repository.DocumentDownloadRepository;
import com.cmcu.itstudy.repository.DocumentRepository;
import com.cmcu.itstudy.repository.DocumentTagRepository;
import com.cmcu.itstudy.repository.DocumentViewRepository;
import com.cmcu.itstudy.repository.UserRepository;
import com.cmcu.itstudy.service.contract.DocumentOperationsService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import jakarta.persistence.criteria.Predicate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DocumentOperationsServiceImpl implements DocumentOperationsService {

    private final DocumentRepository documentRepository;
    private final DocumentViewRepository documentViewRepository;
    private final DocumentDownloadRepository documentDownloadRepository;
    private final DocumentBookmarkRepository documentBookmarkRepository;
    private final UserRepository userRepository;
    private final DocumentCardEnrichmentService documentCardEnrichmentService;

    public DocumentOperationsServiceImpl(DocumentRepository documentRepository,
                                         DocumentViewRepository documentViewRepository,
                                         DocumentDownloadRepository documentDownloadRepository,
                                         DocumentBookmarkRepository documentBookmarkRepository,
                                         UserRepository userRepository,
                                         DocumentCardEnrichmentService documentCardEnrichmentService) {
        this.documentRepository = documentRepository;
        this.documentViewRepository = documentViewRepository;
        this.documentDownloadRepository = documentDownloadRepository;
        this.documentBookmarkRepository = documentBookmarkRepository;
        this.userRepository = userRepository;
        this.documentCardEnrichmentService = documentCardEnrichmentService;
    }

    @Transactional(readOnly = true)
    @Override
    public PagedResponseDocumentCardDto searchDocuments(DocumentListRequestDto request, UUID currentUserId) {
        int page = request.getPage() != null && request.getPage() >= 0 ? request.getPage() : 0;
        int size = request.getSize() != null && request.getSize() > 0 ? request.getSize() : 10;

        Sort sort;
        if ("popular".equalsIgnoreCase(request.getSort())) {
            sort = Sort.by(Sort.Direction.DESC, "downloadCount");
        } else {
            sort = Sort.by(Sort.Direction.DESC, "createdAt");
        }
        Pageable pageable = PageRequest.of(page, size, sort);

        Specification<Document> spec = buildSpecification(request);

        Page<Document> documentPage = documentRepository.findAll(spec, pageable);
        List<Document> documents = documentPage.getContent();

        List<DocumentCardResponseDto> content = documentCardEnrichmentService
                .toEnrichedCardDtos(documents, currentUserId);

        return PagedResponseDocumentCardDto.builder()
                .content(content)
                .page(documentPage.getNumber())
                .size(documentPage.getSize())
                .totalElements((int) documentPage.getTotalElements())
                .totalPages(documentPage.getTotalPages())
                .build();
    }

    @Transactional
    @Override
    public void increaseView(UUID documentId, UUID userId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new NoSuchElementException("Document not found"));

        User user = null;
        boolean alreadyViewed = false;
        if (userId != null) {
            user = userRepository.findById(userId).orElse(null);
            if (user != null && documentViewRepository.existsByDocumentAndUser(document, user)) {
                alreadyViewed = true;
            }
        }

        if (!alreadyViewed) {
            DocumentView view = DocumentView.builder()
                    .document(document)
                    .user(user)
                    .build();
            documentViewRepository.save(view);

            document.setViewCount(document.getViewCount() != null ? document.getViewCount() + 1 : 1L);
        }

        document.setLastViewedAt(LocalDateTime.now());
        documentRepository.save(document);
    }

    @Transactional
    @Override
    public void downloadDocument(UUID documentId, UUID userId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new NoSuchElementException("Document not found"));

        User user = null;
        if (userId != null) {
            user = userRepository.findById(userId).orElse(null);
        }

        DocumentDownload download = DocumentDownload.builder()
                .document(document)
                .user(user)
                .build();
        documentDownloadRepository.save(download);

        document.setDownloadCount(document.getDownloadCount() != null ? document.getDownloadCount() + 1 : 1L);
        document.setLastDownloadedAt(LocalDateTime.now());
        documentRepository.save(document);
    }

    @Transactional
    @Override
    public void toggleBookmark(UUID documentId, UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("UserId is required for bookmark");
        }

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new NoSuchElementException("Document not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        boolean exists = documentBookmarkRepository.existsByUserAndDocumentAndActiveTrue(user, document);
        if (exists) {
            documentBookmarkRepository.deleteByUserAndDocument(user, document);
            long current = document.getBookmarkCount() != null ? document.getBookmarkCount() : 0L;
            document.setBookmarkCount(Math.max(0L, current - 1));
        } else {
            DocumentBookmark bookmark = DocumentBookmark.builder()
                    .document(document)
                    .user(user)
                    .build();
            documentBookmarkRepository.save(bookmark);
            long current = document.getBookmarkCount() != null ? document.getBookmarkCount() : 0L;
            document.setBookmarkCount(current + 1);
        }
        documentRepository.save(document);
    }

    @Transactional(readOnly = true)
    @Override
    public List<DocumentCardResponseDto> getMyBookmarks(UUID userId) {
        if (userId == null) {
            return Collections.emptyList();
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        List<DocumentBookmark> bookmarks = documentBookmarkRepository.findByUserAndActiveTrue(user);
        List<Document> documents = bookmarks.stream()
                .map(DocumentBookmark::getDocument)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return documentCardEnrichmentService.toEnrichedCardDtos(documents, userId);
    }

    private Specification<Document> buildSpecification(DocumentListRequestDto request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("status"), DocumentStatus.APPROVED));
            predicates.add(cb.isFalse(root.get("deleted")));

            if (StringUtils.hasText(request.getKeyword())) {
                predicates.add(cb.like(cb.lower(root.get("title")), "%" + request.getKeyword().toLowerCase() + "%"));
            }

            if (StringUtils.hasText(request.getCategoryId())) {
                UUID categoryId = UUID.fromString(request.getCategoryId());
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }

            if (!CollectionUtils.isEmpty(request.getTagIds())) {
                List<UUID> tagIds = request.getTagIds().stream()
                        .filter(Objects::nonNull)
                        .map(UUID::fromString)
                        .collect(Collectors.toList());
                if (!tagIds.isEmpty()) {
                    var sub = query.subquery(UUID.class);
                    var dt = sub.from(DocumentTag.class);
                    sub.select(dt.get("documentId"));
                    sub.where(dt.get("tagId").in(tagIds));
                    sub.groupBy(dt.get("documentId"));
                    sub.having(cb.equal(cb.countDistinct(dt.get("tagId")), (long) tagIds.size()));

                    predicates.add(root.get("id").in(sub));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
