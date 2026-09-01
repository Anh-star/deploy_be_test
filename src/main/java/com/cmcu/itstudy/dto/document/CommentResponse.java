package com.cmcu.itstudy.dto.document;

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
public class CommentResponse {

    private String id;
    private String authorId;
    private String body;
    private String authorName;
    private String authorAvatar;
    private Integer likeCount;
    private Integer upvoteCount;
    private Integer downvoteCount;
    private Boolean isLiked;
    private String userVote;
    private Integer replyCount;
    private String replyToUserName;
    private Boolean isEdited;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
