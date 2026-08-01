package com.cmcu.itstudy.dto.community;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommunityPostResponseDto {

    private String id;
    private String authorId;
    private String authorName;
    private String authorAvatar;
    private String title;
    private String content;
    private List<String> tags;
    private List<String> imageUrls;
    private List<String> fileUrls;
    private Integer likeCount;
    private Integer upvoteCount;
    private Integer downvoteCount;
    private String currentUserVote; // "UPVOTE", "DOWNVOTE", or null
    private Integer commentCount;
    private Boolean isLiked;
    private Boolean isSaved;
    private PollDto poll;
    private Boolean allowComments;
    private Boolean isHidden;
    private Boolean isReported;
    private Boolean isMuted;
    private Long reportCount;
    private LocalDateTime createdAt;
}
