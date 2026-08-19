package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.dto.community.CommunityPostResponseDto;
import com.cmcu.itstudy.dto.community.CreatePollRequestDto;
import com.cmcu.itstudy.dto.community.PollDto;
import com.cmcu.itstudy.dto.community.PostCommentResponseDto;
import com.cmcu.itstudy.dto.community.VoterDto;

import java.util.List;
import java.util.UUID;

public interface CommunityPostService {

    CommunityPostResponseDto createPost(UUID userId, String content, List<String> imageUrls);

    CommunityPostResponseDto createPost(UUID userId, String title, String content, List<String> tags, List<String> imageUrls, List<String> fileUrls, CreatePollRequestDto pollRequest, Boolean allowComments);

    CommunityPostResponseDto getPostById(UUID postId, UUID currentUserId);

    List<CommunityPostResponseDto> getFeed(int page, int size, UUID currentUserId);

    List<CommunityPostResponseDto> getUserPosts(UUID authorId, int page, int size, UUID currentUserId);

    CommunityPostResponseDto togglePinPost(UUID postId, UUID userId);

    long getFeedTotalCount();

    void deletePost(UUID postId, UUID userId);

    CommunityPostResponseDto toggleLikePost(UUID postId, UUID userId);

    CommunityPostResponseDto votePost(UUID postId, UUID userId, String voteType);

    boolean toggleSavePost(UUID postId, UUID userId);

    List<CommunityPostResponseDto> getSavedPosts(int page, int size, UUID userId);

    PollDto votePollOption(UUID pollId, UUID optionId, UUID userId);

    List<VoterDto> getPollVoters(UUID optionId, UUID currentUserId);

    PollDto addPollOption(UUID pollId, String optionText, UUID userId);

    PostCommentResponseDto addComment(UUID postId, UUID userId, String body, UUID parentCommentId);

    void deleteComment(UUID commentId, UUID userId);

    List<PostCommentResponseDto> getComments(UUID postId, int page, int size, UUID currentUserId);

    List<PostCommentResponseDto> getReplies(UUID commentId, UUID currentUserId);

    PostCommentResponseDto toggleLikeComment(UUID commentId, UUID userId);

    PostCommentResponseDto voteComment(UUID commentId, UUID userId, String voteType);

    CommunityPostResponseDto updatePost(UUID postId, UUID userId, String content, List<String> imageUrls);

    void hardDeletePostPhysics(UUID postId);

    void hardDeleteCommentPhysics(UUID commentId);

    // Report & Moderation
    void reportPost(UUID postId, UUID reporterId, String reasonCode, String detail);

    org.springframework.data.domain.Page<com.cmcu.itstudy.dto.community.PostReportResponseDto> getReportedPosts(String status, String keyword, java.time.LocalDateTime startDate, java.time.LocalDateTime endDate, int page, int size);

    void resolveReport(UUID reportId, UUID resolverId);

    void dismissReport(UUID reportId, UUID resolverId, String reason);

    void dismissReportByPostId(UUID postId, UUID resolverId, String reason);

    void hidePost(UUID postId, UUID moderatorId, String reason);

    void unhidePost(UUID postId, UUID moderatorId, String reason);

    boolean toggleMutePostNotifications(UUID postId, UUID userId);

    void moderatorDeletePost(UUID postId, UUID moderatorId, String reason);

    com.cmcu.itstudy.dto.community.CommunityModerationStatsDto getModerationStats();

    void escalateReport(UUID reportId, UUID moderatorId, String reason);

    org.springframework.data.domain.Page<com.cmcu.itstudy.dto.community.PostReportResponseDto> getEscalatedReports(String keyword, java.time.LocalDateTime startDate, java.time.LocalDateTime endDate, int page, int size);

    void adminBanUserFromReport(UUID reportId, UUID adminId, String reason);

    void adminAcquitReport(UUID reportId, UUID adminId, String reason);
}
