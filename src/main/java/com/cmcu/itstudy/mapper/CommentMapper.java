package com.cmcu.itstudy.mapper;

import com.cmcu.itstudy.dto.document.CommentResponse;
import com.cmcu.itstudy.entity.DocumentComment;

import java.util.UUID;

public final class CommentMapper {

    private CommentMapper() {
    }

    public static CommentResponse toCommentResponse(DocumentComment comment, Boolean isLiked, Integer replyCount) {
        return toCommentResponse(comment, isLiked, replyCount, isLiked != null && isLiked ? "UPVOTE" : null);
    }

    public static CommentResponse toCommentResponse(DocumentComment comment, Boolean isLiked, Integer replyCount, String userVote) {
        if (comment == null) {
            return null;
        }
        String authorId = comment.getAuthor() != null && comment.getAuthor().getId() != null ? comment.getAuthor().getId().toString() : null;
        String authorName = comment.getAuthor() != null ? comment.getAuthor().getFullName() : null;
        String authorAvatar = comment.getAuthor() != null ? comment.getAuthor().getAvatarUrl() : null;
        String replyToUserName = comment.getReplyToUser() != null ? comment.getReplyToUser().getFullName() : null;

        int upvotes = comment.getUpvoteCount() != null ? comment.getUpvoteCount() : (comment.getLikeCount() != null ? comment.getLikeCount() : 0);
        int downvotes = comment.getDownvoteCount() != null ? comment.getDownvoteCount() : 0;
        int netScore = upvotes - downvotes;

        return CommentResponse.builder()
                .id(uuidToString(comment.getId()))
                .authorId(authorId)
                .body(comment.getBody())
                .authorName(authorName)
                .authorAvatar(authorAvatar)
                .likeCount(netScore)
                .upvoteCount(upvotes)
                .downvoteCount(downvotes)
                .isLiked("UPVOTE".equalsIgnoreCase(userVote))
                .userVote(userVote)
                .replyCount(replyCount)
                .replyToUserName(replyToUserName)
                .isEdited(Boolean.TRUE.equals(comment.getIsEdited()))
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }

    private static String uuidToString(UUID id) {
        return id != null ? id.toString() : null;
    }
}
