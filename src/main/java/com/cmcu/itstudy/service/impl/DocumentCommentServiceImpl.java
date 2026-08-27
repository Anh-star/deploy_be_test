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
import java.util.Optional;
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

        DocumentComment saved = documentCommentRepository.save(DocumentComment.builder()
                .document(Document.builder().id(documentId).build())
                .author(author)
                .body(body)
                .likeCount(0)
                .upvoteCount(0)
                .downvoteCount(0)
                .deleted(false)
                .pinned(false)
                .build());

        saved = documentCommentRepository.findByIdWithDocumentAndAuthor(saved.getId()).orElse(saved);

        if (saved.getDocument() != null && saved.getDocument().getCreatedBy() != null) {
            UUID ownerId = saved.getDocument().getCreatedBy().getId();
            User commenter = userRepository.findById(userId).orElse(null);
            String commenterName = (commenter != null && commenter.getFullName() != null) ? commenter.getFullName() : "Ai đó";
            if (!ownerId.equals(userId)) {
                notificationService.createAndPush(
                        ownerId,
                        userId,
                        NotificationType.DOCUMENT_COMMENTED,
                        documentId.toString() + "?commentId=" + saved.getId(),
                        "DOCUMENT",
                        commenterName + " đã bình luận về tài liệu của bạn."
                );
            }
        }

        return CommentMapper.toCommentResponse(saved, false, 0, null);
    }

    @Override
    @Transactional
    public CommentResponse replyComment(UUID parentCommentId, String body, UUID userId) {
        DocumentComment parent = documentCommentRepository.findById(parentCommentId)
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

        if (parent.getAuthor() != null && !parent.getAuthor().getId().equals(userId)) {
            User replier = userRepository.findById(userId).orElse(null);
            String replierName = (replier != null && replier.getFullName() != null) ? replier.getFullName() : "Ai đó";
            notificationService.createAndPush(
                    parent.getAuthor().getId(),
                    userId,
                    NotificationType.COMMENT_REPLIED,
                    parent.getDocument().getId().toString() + "?commentId=" + saved.getId(),
                    "DOCUMENT",
                    replierName + " đã phản hồi bình luận của bạn."
            );
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

        DocumentComment comment = documentCommentRepository.findById(commentId)
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

        return CommentLikeToggleResponseDto.builder()
                .likeCount(upvotes - downvotes)
                .upvoteCount(upvotes)
                .downvoteCount(downvotes)
                .isLiked("UPVOTE".equals(resultVote))
                .userVote(resultVote)
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
