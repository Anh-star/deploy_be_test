package com.cmcu.itstudy.dto.community;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostCommentResponseDto {

    private String id;
    private String parentCommentId;
    private String authorId;
    private String authorName;
    private String authorAvatar;
    private String body;
    private Integer likeCount;
    private Integer upvoteCount;
    private Integer downvoteCount;
    private Integer replyCount;
    private String replyToUserName;
    private Boolean isLiked;
    private String userVote;
    private LocalDateTime createdAt;
    private Integer postCommentCount;
}
