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
public class PostReportResponseDto {

    private String id;
    private String postId;
    private String postTitle;
    private String postContent;
    private String postAuthorId;
    private String postAuthorName;
    private String postAuthorAvatar;
    private String reporterId;
    private String reporterName;
    private String reporterAvatar;
    private String reasonCode;
    private String detail;
    private String status;
    private Long reportCount;
    private Boolean isPostHidden;
    private Boolean isPostDeleted;
    private String escalationReason;
    private String escalatedByName;
    private LocalDateTime escalatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
}
