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
import com.cmcu.itstudy.repository.NotificationRepository;
import com.cmcu.itstudy.service.contract.DocumentCommentService;
import com.cmcu.itstudy.service.contract.NotificationService;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DocumentCommentServiceImpl implements DocumentCommentService {

    private static final int COMMENT_PAGE_SIZE = 5;

    private final DocumentCommentRepository documentCommentRepository;
    private final DocumentCommentLikeRepository documentCommentLikeRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final SseService sseService;

    public DocumentCommentServiceImpl(
            DocumentCommentRepository documentCommentRepository,
            DocumentCommentLikeRepository documentCommentLikeRepository,
            DocumentRepository documentRepository,
            UserRepository userRepository,
            NotificationRepository notificationRepository,
            NotificationService notificationService,
            SseService sseService
    ) {
        this.documentCommentRepository = documentCommentRepository;
        this.documentCommentLikeRepository = documentCommentLikeRepository;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
        this.sseService = sseService;
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

        Map<UUID, String> userVoteMap = new HashMap<>();
        if (currentUserId != null && !rootIds.isEmpty()) {
            List<DocumentCommentLike> likes = documentCommentLikeRepository.findAllByCommentIdInAndUserId(rootIds, currentUserId);
            for (DocumentCommentLike l : likes) {
                userVoteMap.put(l.getComment().getId(), l.getVoteType() != null ? l.getVoteType() : "UPVOTE");
            }
        }

        List<CommentResponse> content = roots.stream()
                .map(c -> {
                    String userVote = userVoteMap.get(c.getId());
                    boolean isLiked = "UPVOTE".equalsIgnoreCase(userVote);
                    return CommentMapper.toCommentResponse(
                            c,
                            isLiked,
                            replyCounts.getOrDefault(c.getId(), 0),
                            userVote
                    );
                })
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

        Map<UUID, String> userVoteMap = new HashMap<>();
        if (currentUserId != null && !replyIds.isEmpty()) {
            List<DocumentCommentLike> likes = documentCommentLikeRepository.findAllByCommentIdInAndUserId(replyIds, currentUserId);
            for (DocumentCommentLike l : likes) {
                userVoteMap.put(l.getComment().getId(), l.getVoteType() != null ? l.getVoteType() : "UPVOTE");
            }
        }

        return replies.stream()
                .map(c -> {
                    String userVote = userVoteMap.get(c.getId());
                    boolean isLiked = "UPVOTE".equalsIgnoreCase(userVote);
                    return CommentMapper.toCommentResponse(
                            c,
                            isLiked,
                            replyCounts.getOrDefault(c.getId(), 0),
                            userVote
                    );
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public CommentResponse createComment(UUID documentId, String body, UUID userId) {
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        Document document = documentRepository.findByIdAndDeletedFalse(documentId)
                .orElseThrow(() -> new NoSuchElementException("Document not found"));

        DocumentComment saved = documentCommentRepository.save(DocumentComment.builder()
                .document(document)
                .author(author)
                .body(body)
                .likeCount(0)
                .upvoteCount(0)
                .downvoteCount(0)
                .deleted(false)
                .pinned(false)
                .build());

        saved = documentCommentRepository.findByIdWithDocumentAndAuthor(saved.getId()).orElse(saved);

        if (document.getCreatedBy() != null) {
            UUID ownerId = document.getCreatedBy().getId();
            String commenterName = (author.getFullName() != null) ? author.getFullName() : "Ai đó";
            if (!ownerId.equals(userId)) {
                String docTitle = (document.getTitle() != null) ? document.getTitle() : "tài liệu";
                String singleMsg = commenterName + " đã bình luận về tài liệu \"" + docTitle + "\"";
                sendAggregatedDocumentCommentNotification(
                        ownerId,
                        author,
                        document,
                        documentId.toString() + "?commentId=" + saved.getId(),
                        NotificationType.DOCUMENT_COMMENTED,
                        singleMsg,
                        false,
                        null
                );
            }
        }

        return CommentMapper.toCommentResponse(saved, false, 0, null);
    }

    @Override
    @Transactional
    public CommentResponse replyComment(UUID parentCommentId, String body, UUID userId) {
        DocumentComment parent = documentCommentRepository.findByIdWithDocumentAndAuthor(parentCommentId)
                .orElseThrow(() -> new NoSuchElementException("Comment not found"));
        if (Boolean.TRUE.equals(parent.getDeleted())) {
            throw new NoSuchElementException("Comment not found");
        }

        User author = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        DocumentComment reply = DocumentComment.builder()
                .document(parent.getDocument())
                .author(author)
                .parent(parent)
                .replyToUser(parent.getAuthor())
                .body(body)
                .likeCount(0)
                .upvoteCount(0)
                .downvoteCount(0)
                .deleted(false)
                .pinned(false)
                .build();

        DocumentComment saved = documentCommentRepository.save(reply);
        DocumentComment forDto = documentCommentRepository.findByIdWithDocumentAndAuthor(saved.getId()).orElse(saved);

        Document document = parent.getDocument();
        if (document != null && (document.getCreatedBy() == null || document.getTitle() == null)) {
            document = documentRepository.findByIdAndDeletedFalse(document.getId()).orElse(document);
        }

        String docTitle = (document != null && document.getTitle() != null) ? document.getTitle() : "tài liệu";
        String replierName = (author.getFullName() != null) ? author.getFullName() : "Ai đó";

        // 1. Gửi thông báo cho tác giả bình luận gốc khi có phản hồi
        if (parent.getAuthor() != null && !parent.getAuthor().getId().equals(userId)) {
            String parentMsg = replierName + " đã trả lời bình luận của bạn trong tài liệu \"" + docTitle + "\"";
            sendAggregatedDocumentCommentNotification(
                    parent.getAuthor().getId(),
                    author,
                    document != null ? document : parent.getDocument(),
                    (document != null ? document.getId() : parent.getDocument().getId()).toString() + "?commentId=" + saved.getId(),
                    NotificationType.COMMENT_REPLIED,
                    parentMsg,
                    true,
                    parent.getId()
            );
        }

        // 2. Gửi thông báo cho chủ sở hữu tài liệu (nếu không phải người trả lời và không phải tác giả bình luận gốc)
        if (document != null && document.getCreatedBy() != null) {
            UUID ownerId = document.getCreatedBy().getId();
            if (!ownerId.equals(userId) && (parent.getAuthor() == null || !ownerId.equals(parent.getAuthor().getId()))) {
                String docOwnerMsg = replierName + " đã bình luận về tài liệu \"" + docTitle + "\"";
                sendAggregatedDocumentCommentNotification(
                        ownerId,
                        author,
                        document,
                        document.getId().toString() + "?commentId=" + saved.getId(),
                        NotificationType.DOCUMENT_COMMENTED,
                        docOwnerMsg,
                        false,
                        null
                );
            }
        }

        return CommentMapper.toCommentResponse(forDto, false, 0, null);
    }

    @Override
    @Transactional
    public CommentLikeToggleResponseDto toggleLike(UUID commentId, UUID userId) {
        return voteComment(commentId, userId, "UPVOTE");
    }

    @Override
    @Transactional
    public CommentLikeToggleResponseDto voteComment(UUID commentId, UUID userId, String voteType) {
        String targetVote = ("DOWNVOTE".equalsIgnoreCase(voteType)) ? "DOWNVOTE" : "UPVOTE";

        DocumentComment comment = documentCommentRepository.findByIdWithDocumentAndAuthor(commentId)
                .orElseThrow(() -> new NoSuchElementException("Comment not found"));
        if (Boolean.TRUE.equals(comment.getDeleted())) {
            throw new NoSuchElementException("Comment not found");
        }

        User userRef = userRepository.getReferenceById(userId);
        Optional<DocumentCommentLike> existing = documentCommentLikeRepository.findByComment_IdAndUser_Id(commentId, userId);

        String resultVote = null;

        if (existing.isPresent()) {
            DocumentCommentLike currentLike = existing.get();
            String currentVoteType = currentLike.getVoteType() != null ? currentLike.getVoteType() : "UPVOTE";

            if (currentVoteType.equalsIgnoreCase(targetVote)) {
                // Toggle off
                documentCommentLikeRepository.delete(currentLike);
                resultVote = null;
            } else {
                // Switch vote type
                currentLike.setVoteType(targetVote);
                documentCommentLikeRepository.save(currentLike);
                resultVote = targetVote;
            }
        } else {
            // New vote
            documentCommentLikeRepository.save(DocumentCommentLike.builder()
                    .comment(comment)
                    .user(userRef)
                    .voteType(targetVote)
                    .build());
            resultVote = targetVote;
        }

        documentCommentLikeRepository.flush();

        // Exact count from database
        int upvotes = (int) documentCommentLikeRepository.countByComment_IdAndVoteType(commentId, "UPVOTE");
        int downvotes = (int) documentCommentLikeRepository.countByComment_IdAndVoteType(commentId, "DOWNVOTE");

        comment.setUpvoteCount(upvotes);
        comment.setDownvoteCount(downvotes);
        comment.setLikeCount(upvotes - downvotes);
        documentCommentRepository.saveAndFlush(comment);

        // Push notification when comment is upvoted / cancelled
        if ("UPVOTE".equals(targetVote) && "UPVOTE".equals(resultVote)) {
            UUID authorId = (comment.getAuthor() != null) ? comment.getAuthor().getId() : null;
            if (authorId != null && !authorId.equals(userId)) {
                try {
                    User liker = userRepository.findById(userId).orElse(null);
                    String likerName = (liker != null && liker.getFullName() != null && !liker.getFullName().isBlank())
                            ? liker.getFullName() : "Ai đó";
                    Document doc = comment.getDocument();
                    if (doc == null && comment.getParent() != null) {
                        doc = comment.getParent().getDocument();
                    }
                    String docTitle = (doc != null && doc.getTitle() != null && !doc.getTitle().isBlank())
                            ? doc.getTitle() : "tài liệu";
                    String docIdStr = (doc != null && doc.getId() != null) ? doc.getId().toString() : "";
                    notificationService.createAndPush(
                            authorId,
                            userId,
                            NotificationType.COMMENT_LIKED,
                            docIdStr + "?commentId=" + comment.getId(),
                            "DOCUMENT",
                            likerName + " đã thích bình luận của bạn trong tài liệu \"" + docTitle + "\""
                    );
                    log.info("Pushed COMMENT_LIKED notification to authorId={}, likerId={}, commentId={}", authorId, userId, comment.getId());
                } catch (Exception ex) {
                    log.warn("Failed to push comment like notification: {}", ex.getMessage());
                }
            } else {
                log.info("Skipped COMMENT_LIKED notification: authorId={}, userId={}", authorId, userId);
            }
        } else {
            // Cancelled comment upvote
            try {
                notificationRepository.deleteByReferenceIdContaining("commentId=" + commentId);
            } catch (Exception ex) {
                log.warn("Failed to delete cancelled comment like notification: {}", ex.getMessage());
            }
        }

        return CommentLikeToggleResponseDto.builder()
                .likeCount(upvotes - downvotes)
                .upvoteCount(upvotes)
                .downvoteCount(downvotes)
                .isLiked("UPVOTE".equals(resultVote))
                .userVote(resultVote)
                .build();
    }

    private void sendAggregatedDocumentCommentNotification(
            UUID recipientId,
            User actor,
            Document document,
            String targetRefId,
            NotificationType type,
            String singleMessage,
            boolean isParentAuthor,
            UUID parentCommentId
    ) {
        if (recipientId == null || actor == null || recipientId.equals(actor.getId())) return;
        UUID docId = document.getId();
        String docTitle = (document.getTitle() != null && !document.getTitle().isBlank()) ? document.getTitle() : "tài liệu";
        try {
            // Check if recipient has an existing notification on this document (read or unread)
            List<com.cmcu.itstudy.entity.Notification> existingList =
                    notificationRepository.findAllDocumentCommentNotifications(recipientId, docId.toString() + "%");

            if (existingList.isEmpty()) {
                // No existing notification: create new single notification
                notificationService.createAndPush(
                        recipientId,
                        actor.getId(),
                        type,
                        targetRefId != null ? targetRefId : docId.toString(),
                        "DOCUMENT",
                        singleMessage
                );
                return;
            }

            // Aggregate with existing notification
            com.cmcu.itstudy.entity.Notification existing = existingList.get(0);

            // Fetch distinct recent commenters for this document or parent comment (excluding the recipient)
            List<String> commenterNames;
            if (isParentAuthor && parentCommentId != null) {
                commenterNames = documentCommentRepository.findDistinctReplierNamesByParentCommentOrderedByRecent(parentCommentId, recipientId);
            } else {
                commenterNames = documentCommentRepository.findDistinctCommenterNamesByDocumentOrderedByRecent(docId, recipientId);
            }

            String actorName = (actor.getFullName() != null && !actor.getFullName().isBlank()) ? actor.getFullName() : "Ai đó";

            String aggregatedMessage;
            if (commenterNames == null || commenterNames.size() <= 1) {
                aggregatedMessage = singleMessage;
            } else if (commenterNames.size() == 2) {
                String first = commenterNames.get(0);
                String second = commenterNames.get(1);
                if (isParentAuthor) {
                    aggregatedMessage = first + " và " + second + " đã trả lời bình luận của bạn trong tài liệu \"" + docTitle + "\"";
                } else {
                    aggregatedMessage = first + " và " + second + " đã bình luận về tài liệu \"" + docTitle + "\"";
                }
            } else {
                String first = commenterNames.get(0);
                int othersCount = commenterNames.size() - 1;
                if (isParentAuthor) {
                    aggregatedMessage = first + " và " + othersCount + " người khác đã trả lời bình luận của bạn trong tài liệu \"" + docTitle + "\"";
                } else {
                    aggregatedMessage = first + " và " + othersCount + " người khác đã bình luận về tài liệu \"" + docTitle + "\"";
                }
            }

            existing.setMessage(aggregatedMessage);
            existing.setActor(actor);
            existing.setType(type);
            existing.setReferenceId(targetRefId != null ? targetRefId : docId.toString());
            existing.setCreatedAt(java.time.LocalDateTime.now());
            existing.setRead(false);
            com.cmcu.itstudy.entity.Notification saved = notificationRepository.save(existing);

            // Clean up any duplicates
            if (existingList.size() > 1) {
                for (int i = 1; i < existingList.size(); i++) {
                    com.cmcu.itstudy.entity.Notification dup = existingList.get(i);
                    notificationRepository.delete(dup);
                    try {
                        Map<String, Object> removeData = new HashMap<>();
                        removeData.put("id", dup.getId().toString());
                        removeData.put("action", "DELETE");
                        sseService.pushEvent(recipientId, "notification-removed", removeData);
                    } catch (Exception ignored) {}
                }
            }

            // Push updated notification via SSE to recipient
            try {
                com.cmcu.itstudy.dto.notification.NotificationResponseDto dto = com.cmcu.itstudy.dto.notification.NotificationResponseDto.builder()
                        .id(saved.getId().toString())
                        .actorId(actor.getId().toString())
                        .actorName(actorName)
                        .actorAvatar(actor.getAvatarUrl())
                        .type(saved.getType())
                        .referenceId(saved.getReferenceId())
                        .referenceType(saved.getReferenceType())
                        .message(saved.getMessage())
                        .isRead(false)
                        .createdAt(saved.getCreatedAt())
                        .build();
                sseService.pushEvent(recipientId, "notification", dto);
            } catch (Exception sseEx) {
                log.warn("Failed to push aggregated SSE notification to user {}: {}", recipientId, sseEx.getMessage());
            }

        } catch (Exception ex) {
            log.warn("Failed to send aggregated document comment notification: {}", ex.getMessage());
        }
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
