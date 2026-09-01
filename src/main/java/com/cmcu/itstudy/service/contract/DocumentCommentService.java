package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.dto.document.CommentLikeToggleResponseDto;
import com.cmcu.itstudy.dto.document.CommentResponse;
import com.cmcu.itstudy.dto.document.CommentThreadPageResponseDto;

import java.util.List;
import java.util.UUID;

public interface DocumentCommentService {

    CommentThreadPageResponseDto getComments(UUID documentId, int page, UUID currentUserId);

    List<CommentResponse> getReplies(UUID commentId, UUID currentUserId);

    CommentResponse createComment(UUID documentId, String body, UUID userId);

    CommentResponse replyComment(UUID parentCommentId, String body, UUID userId);

    CommentLikeToggleResponseDto toggleLike(UUID commentId, UUID userId);

    CommentLikeToggleResponseDto voteComment(UUID commentId, UUID userId, String voteType);

    CommentResponse editComment(UUID commentId, String newBody, UUID userId);

    void deleteComment(UUID commentId, UUID userId);

    List<com.cmcu.itstudy.dto.document.CommentEditHistoryDto> getEditHistory(UUID commentId);
}
