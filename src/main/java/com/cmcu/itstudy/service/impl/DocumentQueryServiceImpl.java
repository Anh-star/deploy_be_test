package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.dto.document.DocumentCardResponseDto;
import com.cmcu.itstudy.dto.document.DocumentUploaderDto;
import com.cmcu.itstudy.dto.document.DocumentDetailQuizDto;
import com.cmcu.itstudy.dto.document.DocumentDetailResponseDto;
import com.cmcu.itstudy.dto.document.DocumentFileUrlResponseDto;
import com.cmcu.itstudy.dto.document.DocumentPrimaryFileDto;
import com.cmcu.itstudy.dto.document.DocumentRelatedDocumentDto;
import com.cmcu.itstudy.dto.document.QuizListPageResponseDto;
import com.cmcu.itstudy.entity.Document;
import com.cmcu.itstudy.mapper.DocumentMapper;
import com.cmcu.itstudy.repository.DocumentFileRepository;
import com.cmcu.itstudy.security.UserDetailsImpl;
import com.cmcu.itstudy.service.contract.CommentService;
import com.cmcu.itstudy.service.contract.DocumentAccessService;
import com.cmcu.itstudy.service.contract.DocumentQueryService;
import com.cmcu.itstudy.service.contract.DocumentService;
import com.cmcu.itstudy.service.contract.QuizService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DocumentQueryServiceImpl implements DocumentQueryService {

    private static final int RELATED_LIMIT = 5;

    private final DocumentService documentService;
    private final CommentService commentService;
    private final QuizService quizService;
    private final DocumentFileRepository documentFileRepository;
    private final DocumentCardEnrichmentService documentCardEnrichmentService;
    private final DocumentAccessService documentAccessService;

    public DocumentQueryServiceImpl(DocumentService documentService,
                                    CommentService commentService,
                                    QuizService quizService,
                                    DocumentFileRepository documentFileRepository,
                                    DocumentCardEnrichmentService documentCardEnrichmentService,
                                    DocumentAccessService documentAccessService) {
        this.documentService = documentService;
        this.commentService = commentService;
        this.quizService = quizService;
        this.documentFileRepository = documentFileRepository;
        this.documentCardEnrichmentService = documentCardEnrichmentService;
        this.documentAccessService = documentAccessService;
    }

    @Transactional(readOnly = true)
    @Override
    public DocumentDetailResponseDto getDocumentDetail(UUID id, UUID currentUserId) {
        Document document = documentService.getById(id);

        DocumentPrimaryFileDto primaryFile = documentFileRepository.findByDocumentIdAndPrimaryTrue(id)
                .map(f -> DocumentMapper.toPrimaryFileDto(f, document))
                .orElseGet(() -> DocumentMapper.legacyPrimaryFromDocument(document));

        // Same source as list/home cards: denormalized counters on Document (updated by increaseView / downloadDocument).
        long totalViews = document.getViewCount() != null ? document.getViewCount() : 0L;
        long totalDownloads = document.getDownloadCount() != null ? document.getDownloadCount() : 0L;
        long reportCount = document.getReports() != null ? document.getReports().size() : 0L;

        var comments = commentService.loadCommentsForDocument(id);
        List<DocumentDetailQuizDto> quizzes = quizService.loadQuizzesForDocument(id);

        List<DocumentRelatedDocumentDto> related = documentService.getRelatedDocuments(id, RELATED_LIMIT).stream()
                .map(DocumentMapper::toRelatedDocumentDto)
                .collect(Collectors.toList());

        List<DocumentCardResponseDto> cards = documentCardEnrichmentService
                .toEnrichedCardDtos(List.of(document), currentUserId);
        DocumentCardResponseDto card0 = cards.isEmpty() ? null : cards.get(0);
        String authorName = card0 != null ? card0.getAuthorName() : null;
        String uploaderUserId = card0 != null ? card0.getUserId() : null;
        DocumentUploaderDto uploader = card0 != null ? card0.getUploader() : null;
        List<String> tags = Collections.emptyList();
        if (card0 != null && card0.getTags() != null) {
            tags = card0.getTags();
        }

        Boolean hasAccess = null;
        if (currentUserId != null) {
            hasAccess = documentAccessService.hasAccess(currentUserId, id);
        }

        boolean isOwner = currentUserId != null
                && document.getCreatedBy() != null
                && document.getCreatedBy().getId() != null
                && document.getCreatedBy().getId().equals(currentUserId);
        if (Boolean.TRUE.equals(document.getIsPaid())
                && !isOwner
                && (currentUserId == null || !Boolean.TRUE.equals(hasAccess))) {
            if (primaryFile != null) {
                primaryFile.setFileUrl(null);
            }
        }

        return DocumentMapper.toDetailResponseDto(
                document,
                authorName,
                uploaderUserId,
                uploader,
                tags,
                totalViews,
                totalDownloads,
                reportCount,
                primaryFile,
                comments,
                quizzes,
                related,
                hasAccess
        );
    }

    @Transactional(readOnly = true)
    @Override
    public DocumentFileUrlResponseDto getDocumentPrimaryFileUrl(UUID documentId) {
        Document document = documentService.getById(documentId);

        DocumentFileUrlResponseDto dto = documentFileRepository.findByDocumentIdAndPrimaryTrue(documentId)
                .map(f -> DocumentMapper.toFileUrlResponseDto(f, document))
                .orElseGet(() -> DocumentMapper.toFileUrlResponseDto(null, document));
        if (dto == null || dto.getFileUrl() == null || dto.getFileUrl().isBlank()) {
            throw new NoSuchElementException("Primary document file not found");
        }

        if (Boolean.TRUE.equals(document.getIsPaid())) {
            UUID userId = getCurrentUserIdOrNull();
            if (userId != null && !documentAccessService.hasAccess(userId, documentId)) {
                throw new AccessDeniedException("You must purchase this document before downloading.");
            }
        }

        return dto;
    }

    private UUID getCurrentUserIdOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetailsImpl userDetails)) {
            return null;
        }
        return userDetails.getUser().getId();
    }

    @Transactional(readOnly = true)
    @Override
    public QuizListPageResponseDto getQuizzesByDocument(UUID documentId, int page, int size) {
        return quizService.getQuizzesByDocument(documentId, page, size);
    }
}
