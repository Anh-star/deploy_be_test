package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.dto.community.CommunityPostResponseDto;
import com.cmcu.itstudy.dto.community.CreatePollRequestDto;
import com.cmcu.itstudy.dto.community.PollDto;
import com.cmcu.itstudy.dto.community.PostCommentResponseDto;

import java.util.List;
import java.util.UUID;

public interface CommunityPostService {

    CommunityPostResponseDto createPost(UUID userId, String content, List<String> imageUrls);

    CommunityPostResponseDto createPost(UUID userId, String content, List<String> imageUrls, List<String> fileUrls, CreatePollRequestDto pollRequest);

    CommunityPostResponseDto getPostById(UUID postId, UUID currentUserId);

    List<CommunityPostResponseDto> getFeed(int page, int size, UUID currentUserId);

    long getFeedTotalCount();

    void deletePost(UUID postId, UUID userId);

    CommunityPostResponseDto toggleLikePost(UUID postId, UUID userId);

    CommunityPostResponseDto votePost(UUID postId, UUID userId, String voteType);

    boolean toggleSavePost(UUID postId, UUID userId);

    List<CommunityPostResponseDto> getSavedPosts(int page, int size, UUID userId);

    PollDto votePollOption(UUID pollId, UUID optionId, UUID userId);

    PostCommentResponseDto addComment(UUID postId, UUID userId, String body, UUID parentCommentId);

    void deleteComment(UUID commentId, UUID userId);

    List<PostCommentResponseDto> getComments(UUID postId, int page, int size, UUID currentUserId);

    List<PostCommentResponseDto> getReplies(UUID commentId, UUID currentUserId);

    PostCommentResponseDto toggleLikeComment(UUID commentId, UUID userId);

    CommunityPostResponseDto updatePost(UUID postId, UUID userId, String content, List<String> imageUrls);

    void hardDeletePostPhysics(UUID postId);

    void hardDeleteCommentPhysics(UUID commentId);
}
