package com.cmcu.itstudy.mapper;

import com.cmcu.itstudy.dto.community.CommunityPostResponseDto;
import com.cmcu.itstudy.dto.community.PostCommentResponseDto;
import com.cmcu.itstudy.entity.CommunityPost;
import com.cmcu.itstudy.entity.CommunityPostComment;
import com.cmcu.itstudy.entity.CommunityPostImage;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public final class CommunityPostMapper {

    private CommunityPostMapper() {
    }

    public static CommunityPostResponseDto toPostResponse(
            CommunityPost post,
            List<CommunityPostImage> images,
            Boolean isLiked
    ) {
        if (post == null) return null;

        String authorName = post.getAuthor() != null ? post.getAuthor().getFullName() : null;
        String authorAvatar = post.getAuthor() != null ? post.getAuthor().getAvatarUrl() : null;
        String authorId = post.getAuthor() != null ? uuidToString(post.getAuthor().getId()) : null;

        List<String> imageUrls = images != null
                ? images.stream().map(CommunityPostImage::getImageUrl).collect(Collectors.toList())
                : List.of();

        return CommunityPostResponseDto.builder()
                .id(uuidToString(post.getId()))
                .authorId(authorId)
                .authorName(authorName)
                .authorAvatar(authorAvatar)
                .content(post.getContent())
                .imageUrls(imageUrls)
                .likeCount(post.getLikeCount())
                .commentCount(post.getCommentCount())
                .isLiked(isLiked)
                .createdAt(post.getCreatedAt())
                .build();
    }

    public static PostCommentResponseDto toCommentResponse(
            CommunityPostComment comment,
            Integer replyCount,
            Boolean isLiked
    ) {
        if (comment == null) return null;

        String authorName = comment.getAuthor() != null ? comment.getAuthor().getFullName() : null;
        String authorAvatar = comment.getAuthor() != null ? comment.getAuthor().getAvatarUrl() : null;
        String authorId = comment.getAuthor() != null ? uuidToString(comment.getAuthor().getId()) : null;
        String replyToUserName = comment.getReplyToUser() != null ? comment.getReplyToUser().getFullName() : null;

        return PostCommentResponseDto.builder()
                .id(uuidToString(comment.getId()))
                .authorId(authorId)
                .authorName(authorName)
                .authorAvatar(authorAvatar)
                .body(comment.getBody())
                .likeCount(comment.getLikeCount())
                .replyCount(replyCount)
                .replyToUserName(replyToUserName)
                .isLiked(isLiked)
                .createdAt(comment.getCreatedAt())
                .build();
    }

    private static String uuidToString(UUID id) {
        return id != null ? id.toString() : null;
    }
}
