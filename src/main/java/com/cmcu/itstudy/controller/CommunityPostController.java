package com.cmcu.itstudy.controller;

import com.cmcu.itstudy.dto.common.ApiResponse;
import com.cmcu.itstudy.dto.community.CommunityPostResponseDto;
import com.cmcu.itstudy.dto.community.CreatePostCommentRequestDto;
import com.cmcu.itstudy.dto.community.CreatePostRequestDto;
import com.cmcu.itstudy.dto.community.PollDto;
import com.cmcu.itstudy.dto.community.PostCommentResponseDto;
import com.cmcu.itstudy.dto.community.PostEditHistoryDto;
import com.cmcu.itstudy.dto.community.VotePostRequestDto;
import com.cmcu.itstudy.dto.community.VoterDto;
import com.cmcu.itstudy.security.UserDetailsImpl;
import com.cmcu.itstudy.service.contract.CommunityPostService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/community/posts")
public class CommunityPostController {

    private final CommunityPostService communityPostService;

    public CommunityPostController(CommunityPostService communityPostService) {
        this.communityPostService = communityPostService;
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CommunityPostResponseDto>> createPost(
            @Valid @RequestBody CreatePostRequestDto request,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        UUID userId = currentUser.getUser().getId();
        CommunityPostResponseDto data = communityPostService.createPost(
                userId, request.getTitle(), request.getContent(), request.getTags(), request.getImageUrls(), request.getFileUrls(), request.getPoll(), request.getAllowComments()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(data, "Post created"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getFeed(
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        UUID userId = currentUser != null ? currentUser.getUser().getId() : null;
        List<CommunityPostResponseDto> posts = communityPostService.getFeed(page, size, userId);
        long total = communityPostService.getFeedTotalCount();

        Map<String, Object> result = new HashMap<>();
        result.put("content", posts);
        result.put("page", page);
        result.put("size", size);
        result.put("totalElements", total);

        return ResponseEntity.ok(ApiResponse.success(result, "Feed"));
    }

    @GetMapping("/user/{authorId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserPosts(
            @PathVariable UUID authorId,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        UUID userId = currentUser != null ? currentUser.getUser().getId() : null;
        List<CommunityPostResponseDto> posts = communityPostService.getUserPosts(authorId, page, size, userId);

        Map<String, Object> result = new HashMap<>();
        result.put("content", posts);
        result.put("page", page);
        result.put("size", size);
        result.put("totalElements", posts.size());

        return ResponseEntity.ok(ApiResponse.success(result, "User posts"));
    }

    @PostMapping("/{postId}/pin")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CommunityPostResponseDto>> togglePin(
            @PathVariable UUID postId,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        UUID userId = currentUser.getUser().getId();
        CommunityPostResponseDto data = communityPostService.togglePinPost(postId, userId);
        return ResponseEntity.ok(ApiResponse.success(data, Boolean.TRUE.equals(data.getIsPinned()) ? "Đã ghim bài viết" : "Đã bỏ ghim bài viết"));
    }

    @GetMapping("/saved")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSavedPosts(
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        UUID userId = currentUser.getUser().getId();
        List<CommunityPostResponseDto> posts = communityPostService.getSavedPosts(page, size, userId);

        Map<String, Object> result = new HashMap<>();
        result.put("content", posts);
        result.put("page", page);
        result.put("size", size);
        result.put("totalElements", posts.size());

        return ResponseEntity.ok(ApiResponse.success(result, "Saved posts"));
    }

    @GetMapping("/{postId}")
    public ResponseEntity<ApiResponse<CommunityPostResponseDto>> getPostById(
            @PathVariable UUID postId,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        UUID userId = currentUser != null ? currentUser.getUser().getId() : null;
        CommunityPostResponseDto data = communityPostService.getPostById(postId, userId);
        return ResponseEntity.ok(ApiResponse.success(data, "Post detail"));
    }

    @DeleteMapping("/{postId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deletePost(
            @PathVariable UUID postId,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        UUID userId = currentUser.getUser().getId();
        communityPostService.deletePost(postId, userId);
        return ResponseEntity.ok(ApiResponse.success(null, "Post deleted"));
    }

    @GetMapping("/{postId}/edit-history")
    public ResponseEntity<ApiResponse<List<com.cmcu.itstudy.dto.community.PostEditHistoryDto>>> getPostEditHistory(
            @PathVariable UUID postId
    ) {
        List<com.cmcu.itstudy.dto.community.PostEditHistoryDto> history = communityPostService.getPostEditHistory(postId);
        return ResponseEntity.ok(ApiResponse.success(history, "Lịch sử chỉnh sửa bài viết"));
    }

    @PostMapping("/{postId}/report")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> reportPost(
            @PathVariable UUID postId,
            @Valid @RequestBody com.cmcu.itstudy.dto.community.ReportPostRequestDto request,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        communityPostService.reportPost(postId, currentUser.getUser().getId(), request.getReasonCode(), request.getDetail());
        return ResponseEntity.ok(ApiResponse.success(null, "Đã gửi báo cáo bài viết thành công"));
    }

    @PostMapping("/{postId}/like")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CommunityPostResponseDto>> toggleLike(
            @PathVariable UUID postId,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        UUID userId = currentUser.getUser().getId();
        CommunityPostResponseDto data = communityPostService.votePost(postId, userId, "UPVOTE");
        return ResponseEntity.ok(ApiResponse.success(data, "Like toggled"));
    }

    @PostMapping("/{postId}/vote")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CommunityPostResponseDto>> votePost(
            @PathVariable UUID postId,
            @RequestBody(required = false) VotePostRequestDto request,
            @RequestParam(name = "voteType", required = false) String paramVoteType,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        UUID userId = currentUser.getUser().getId();
        String voteType = (request != null && request.getVoteType() != null)
                ? request.getVoteType()
                : (paramVoteType != null ? paramVoteType : "UPVOTE");
        CommunityPostResponseDto data = communityPostService.votePost(postId, userId, voteType);
        return ResponseEntity.ok(ApiResponse.success(data, "Post voted"));
    }

    @PostMapping("/{postId}/save")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> toggleSave(
            @PathVariable UUID postId,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        UUID userId = currentUser.getUser().getId();
        boolean isSaved = communityPostService.toggleSavePost(postId, userId);
        Map<String, Object> result = new HashMap<>();
        result.put("isSaved", isSaved);
        return ResponseEntity.ok(ApiResponse.success(result, isSaved ? "Post saved" : "Post unsaved"));
    }

    @PostMapping(value = {"/{postId}/toggle-notifications", "/{postId}/notifications/mute"})
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> toggleNotifications(
            @PathVariable UUID postId,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        UUID userId = currentUser.getUser().getId();
        boolean isMuted = communityPostService.toggleMutePostNotifications(postId, userId);
        Map<String, Object> result = new HashMap<>();
        result.put("isMuted", isMuted);
        return ResponseEntity.ok(ApiResponse.success(result, isMuted ? "Đã tắt thông báo bài viết" : "Đã bật thông báo bài viết"));
    }

    @PostMapping("/polls/{pollId}/options/{optionId}/vote")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PollDto>> votePollOption(
            @PathVariable UUID pollId,
            @PathVariable UUID optionId,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        UUID userId = currentUser.getUser().getId();
        PollDto data = communityPostService.votePollOption(pollId, optionId, userId);
        return ResponseEntity.ok(ApiResponse.success(data, "Poll option voted"));
    }

    @GetMapping("/polls/options/{optionId}/voters")
    public ResponseEntity<ApiResponse<List<VoterDto>>> getPollVoters(
            @PathVariable UUID optionId,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        UUID userId = currentUser != null ? currentUser.getUser().getId() : null;
        List<VoterDto> voters = communityPostService.getPollVoters(optionId, userId);
        return ResponseEntity.ok(ApiResponse.success(voters, "Poll option voters"));
    }

    @PostMapping("/polls/{pollId}/options")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PollDto>> addPollOption(
            @PathVariable UUID pollId,
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        UUID userId = currentUser.getUser().getId();
        String optionText = request != null ? request.get("optionText") : null;
        PollDto data = communityPostService.addPollOption(pollId, optionText, userId);
        return ResponseEntity.ok(ApiResponse.success(data, "Poll option added"));
    }

    @DeleteMapping("/polls/options/{optionId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PollDto>> deletePollOption(
            @PathVariable UUID optionId,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        UUID userId = currentUser.getUser().getId();
        PollDto data = communityPostService.deletePollOption(optionId, userId);
        return ResponseEntity.ok(ApiResponse.success(data, "Poll option deleted"));
    }

    @GetMapping("/{postId}/comments")
    public ResponseEntity<ApiResponse<List<PostCommentResponseDto>>> getComments(
            @PathVariable UUID postId,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        UUID userId = currentUser != null ? currentUser.getUser().getId() : null;
        List<PostCommentResponseDto> data = communityPostService.getComments(postId, page, size, userId);
        return ResponseEntity.ok(ApiResponse.success(data, "Comments"));
    }

    @PostMapping("/{postId}/comments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PostCommentResponseDto>> addComment(
            @PathVariable UUID postId,
            @Valid @RequestBody CreatePostCommentRequestDto request,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        UUID userId = currentUser.getUser().getId();
        UUID parentId = parseUuid(request.getParentCommentId());
        PostCommentResponseDto data = communityPostService.addComment(postId, userId, request.getBody(), parentId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(data, "Comment added"));
    }

    @DeleteMapping("/comments/{commentId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @PathVariable UUID commentId,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        UUID userId = currentUser.getUser().getId();
        communityPostService.deleteComment(commentId, userId);
        return ResponseEntity.ok(ApiResponse.success(null, "Comment deleted"));
    }

    @GetMapping("/comments/{commentId}/replies")
    public ResponseEntity<ApiResponse<List<PostCommentResponseDto>>> getReplies(
            @PathVariable UUID commentId,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        UUID userId = currentUser != null ? currentUser.getUser().getId() : null;
        List<PostCommentResponseDto> data = communityPostService.getReplies(commentId, userId);
        return ResponseEntity.ok(ApiResponse.success(data, "Replies"));
    }

    @PostMapping("/comments/{commentId}/like")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PostCommentResponseDto>> toggleCommentLike(
            @PathVariable UUID commentId,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        UUID userId = currentUser.getUser().getId();
        PostCommentResponseDto data = communityPostService.voteComment(commentId, userId, "UPVOTE");
        return ResponseEntity.ok(ApiResponse.success(data, "Comment like toggled"));
    }

    @PostMapping("/comments/{commentId}/vote")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PostCommentResponseDto>> voteComment(
            @PathVariable UUID commentId,
            @RequestParam(name = "type", defaultValue = "UPVOTE") String voteType,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        UUID userId = currentUser.getUser().getId();
        PostCommentResponseDto data = communityPostService.voteComment(commentId, userId, voteType);
        return ResponseEntity.ok(ApiResponse.success(data, "Comment vote updated"));
    }

    @PutMapping("/{postId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CommunityPostResponseDto>> updatePost(
            @PathVariable UUID postId,
            @Valid @RequestBody CreatePostRequestDto request,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        UUID userId = currentUser.getUser().getId();
        CommunityPostResponseDto data = communityPostService.updatePost(postId, userId, request);
        return ResponseEntity.ok(ApiResponse.success(data, "Post updated"));
    }

    private UUID parseUuid(String str) {
        if (str == null || str.isBlank()) return null;
        try { return UUID.fromString(str.trim()); } catch (Exception e) { return null; }
    }
}
