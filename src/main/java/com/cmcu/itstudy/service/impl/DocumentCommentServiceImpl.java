package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.dto.document.CommentLikeToggleResponseDto;
import com.cmcu.itstudy.dto.document.CommentResponse;
import com.cmcu.itstudy.dto.document.CommentThreadPageResponseDto;
import com.cmcu.itstudy.entity.Document;
import com.cmcu.itstudy.entity.DocumentComment;
import com.cmcu.itstudy.entity.DocumentCommentLike;
import com.cmcu.itstudy.entity.User;
import com.cmcu.itstudy.enums.NotificationType;
import com.cmcu.itstudy.mapper.CommentMapper;
import com.cmcu.itstudy.repository.DocumentCommentLikeRepository;
import com.cmcu.itstudy.repository.DocumentCommentRepository;
import com.cmcu.itstudy.repository.DocumentRepository;
import com.cmcu.itstudy.repository.UserRepository;
import com.cmcu.itstudy.service.contract.DocumentCommentService;
import com.cmcu.itstudy.service.contract.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DocumentCommentServiceImpl implements DocumentCommentService {

    private static final int COMMENT_PAGE_SIZE = 5;

    private final DocumentCommentRepository documentCommentRepository;
    private final DocumentCommentLikeRepository documentCommentLikeRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public DocumentCommentServiceImpl(
            DocumentCommentRepository documentCommentRepository,
            DocumentCommentLikeRepository documentCommentLikeRepository,
            DocumentRepository documentRepository,
            UserRepository userRepository,
            NotificationService notificationService
    ) {
        this.documentCommentRepository = documentCommentRepository;
        this.documentCommentLikeRepository = documentCommentLikeRepository;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional(readOnly = true)
    public CommentThreadPageResponseDto getComments(UUID documentId, int page, UUID currentUserId) {
        if (!documentRepository.existsById(documentId)) {
            throw new NoSuchElementException("Document not found");
        }

        Page<DocumentComment> rootPage = documentCommentRepository
                .findByDocument_IdAndDeletedFalseAndParentIsNullOrderByLikeCountDescCreatedAtDesc(
                        documentId,
                        PageRequest.of(page, COMMENT_PAGE_SIZE)
                );

        List<DocumentComment> roots = rootPage.getContent();
        List<UUID> rootIds = roots.stream().map(DocumentComment::getId).toList();

        Map<UUID, Integer> replyCounts = rootIds.isEmpty()
                ? Map.of()
                : toReplyCountMap(documentCommentRepository.countDirectRepliesByParentIds(rootIds));

        Set<UUID> likedIds = new HashSet<>();
        if (currentUserId != null && !rootIds.isEmpty()) {
            likedIds.addAll(documentCommentLikeRepository.findLikedCommentIds(rootIds, currentUserId));
        }

        List<CommentResponse> content = roots.stream()
                .map(c -> CommentMapper.toCommentResponse(
                        c,
                        currentUserId != null && likedIds.contains(c.getId()),
                        replyCounts.getOrDefault(c.getId(), 0)
                ))
                .collect(Collectors.toList());

        long totalComment = documentCommentRepository.countByDocumentId(documentId);

        return CommentThreadPageResponseDto.builder()
                .content(content)
                .totalComment(totalComment)
                .page(rootPage.getNumber())
                .size(rootPage.getSize())
                .totalElements(rootPage.getTotalElements())
                .totalPages(rootPage.getTotalPages())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentResponse> getReplies(UUID commentId, UUID currentUserId) {
        DocumentComment parent = documentCommentRepository.findById(commentId)
                .orElseThrow(() -> new NoSuchElementException("Comment not found"));
        if (Boolean.TRUE.equals(parent.getDeleted())) {
            throw new NoSuchElementException("Comment not found");
        }

        List<DocumentComment> replies = documentCommentRepository.findByParent_IdAndDeletedFalseOrderByCreatedAtAsc(commentId);
        List<UUID> replyIds = replies.stream().map(DocumentComment::getId).toList();

        Map<UUID, Integer> replyCounts = replyIds.isEmpty()
                ? Map.of()
                : toReplyCountMap(documentCommentRepository.countDirectRepliesByParentIds(replyIds));

        Set<UUID> likedIds = new HashSet<>();
        if (currentUserId != null && !replyIds.isEmpty()) {
            likedIds.addAll(documentCommentLikeRepository.findLikedCommentIds(replyIds, currentUserId));
        }

        return replies.stream()
                .map(c -> CommentMapper.toCommentResponse(
                        c,
                        currentUserId != null && likedIds.contains(c.getId()),
                        replyCounts.getOrDefault(c.getId(), 0)
                ))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public CommentResponse createComment(UUID documentId, String body, UUID userId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new NoSuchElementException("Document not found"));
        if (Boolean.TRUE.equals(document.getDeleted())) {
            throw new NoSuchElementException("Document not found");
        }

        User author = userRepository.getReferenceById(userId);
        DocumentComment saved = documentCommentRepository.save(DocumentComment.builder()
                .document(document)
                .author(author)
                .body(body)
                .build());

        saved = documentCommentRepository.findByIdWithDocumentAndAuthor(saved.getId()).orElse(saved);

        if (document.getCreatedBy() != null && !userId.equals(document.getCreatedBy().getId())) {
            User commenter = userRepository.findById(userId).orElse(null);
            String commenterName = (commenter != null && commenter.getFullName() != null) ? commenter.getFullName() : "Ai đó";
            notificationService.createAndPush(
                    document.getCreatedBy().getId(),
                    userId,
                    NotificationType.DOCUMENT_COMMENTED,
                    document.getId().toString(),
                    "DOCUMENT",
                    commenterName + " đã bình luận về tài liệu của bạn"
            );
        }

        return CommentMapper.toCommentResponse(saved, false, 0);
    }

    @Override
    @Transactional
    public CommentResponse replyComment(UUID parentCommentId, String body, UUID userId) {
        DocumentComment parent = documentCommentRepository.findByIdWithDocumentAndAuthor(parentCommentId)
                .orElseThrow(() -> new NoSuchElementException("Comment not found"));
        if (Boolean.TRUE.equals(parent.getDeleted())) {
            throw new NoSuchElementException("Comment not found");
        }

        User author = userRepository.getReferenceById(userId);
        DocumentComment saved = documentCommentRepository.save(DocumentComment.builder()
                .document(parent.getDocument())
                .parent(parent)
                .replyToUser(parent.getAuthor())
                .author(author)
                .body(body)
                .build());

        DocumentComment forDto = documentCommentRepository
                .findByIdWithDocumentAuthorAndReplyTo(saved.getId())
                .orElse(saved);

        if (parent.getAuthor() != null && !userId.equals(parent.getAuthor().getId())) {
            User replier = userRepository.findById(userId).orElse(null);
            String replierName = (replier != null && replier.getFullName() != null) ? replier.getFullName() : "Ai đó";
            notificationService.createAndPush(
                    parent.getAuthor().getId(),
                    userId,
                    NotificationType.COMMENT_REPLIED,
                    parent.getDocument().getId().toString(),
                    "DOCUMENT",
                    replierName + " đã phản hồi bình luận tài liệu của bạn"
            );
        }

        return CommentMapper.toCommentResponse(forDto, false, 0);
    }

    @Override
    @Transactional
    public CommentLikeToggleResponseDto toggleLike(UUID commentId, UUID userId) {
        DocumentComment comment = documentCommentRepository.findById(commentId)
                .orElseThrow(() -> new NoSuchElementException("Comment not found"));
        if (Boolean.TRUE.equals(comment.getDeleted())) {
            throw new NoSuchElementException("Comment not found");
        }

        User userRef = userRepository.getReferenceById(userId);
        var existing = documentCommentLikeRepository.findByComment_IdAndUser_Id(commentId, userId);

        if (existing.isPresent()) {
            documentCommentLikeRepository.delete(existing.get());
            documentCommentLikeRepository.flush();
            int next = Math.max(0, comment.getLikeCount() - 1);
            comment.setLikeCount(next);
            documentCommentRepository.save(comment);
            return CommentLikeToggleResponseDto.builder()
                    .likeCount(next)
                    .isLiked(false)
                    .build();
        }

        documentCommentLikeRepository.save(DocumentCommentLike.builder()
                .comment(comment)
                .user(userRef)
                .build());
        int next = comment.getLikeCount() + 1;
        comment.setLikeCount(next);
        documentCommentRepository.save(comment);

        return CommentLikeToggleResponseDto.builder()
                .likeCount(next)
                .isLiked(true)
                .build();
    }

    private static Map<UUID, Integer> toReplyCountMap(List<Object[]> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<UUID, Integer> map = new HashMap<>();
        for (Object[] row : rows) {
            if (row == null || row.length < 2 || row[0] == null || !(row[1] instanceof Number)) {
                continue;
            }
            UUID parentId = (UUID) row[0];
            int cnt = ((Number) row[1]).intValue();
            map.put(parentId, cnt);
        }
        return map;
    }
}
