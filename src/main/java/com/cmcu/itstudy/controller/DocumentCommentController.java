package com.cmcu.itstudy.controller;

import com.cmcu.itstudy.dto.common.ApiResponse;
import com.cmcu.itstudy.dto.document.CommentLikeToggleResponseDto;
import com.cmcu.itstudy.dto.document.CommentResponse;
import com.cmcu.itstudy.dto.document.CommentThreadPageResponseDto;
import com.cmcu.itstudy.dto.document.CreateCommentRequestDto;
import com.cmcu.itstudy.security.UserDetailsImpl;
import com.cmcu.itstudy.service.contract.DocumentCommentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api")
public class DocumentCommentController {

    private final DocumentCommentService documentCommentService;

    public DocumentCommentController(DocumentCommentService documentCommentService) {
        this.documentCommentService = documentCommentService;
    }

    @GetMapping("/documents/{id}/comments")
    public ResponseEntity<ApiResponse<CommentThreadPageResponseDto>> getComments(
            @PathVariable("id") UUID documentId,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        UUID userId = currentUser != null ? currentUser.getUser().getId() : null;
        CommentThreadPageResponseDto data = documentCommentService.getComments(documentId, page, userId);
        return ResponseEntity.ok(ApiResponse.success(data, "Comments"));
    }

    @GetMapping("/comments/{id}/replies")
    public ResponseEntity<ApiResponse<List<CommentResponse>>> getReplies(
            @PathVariable("id") UUID commentId,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        UUID userId = currentUser != null ? currentUser.getUser().getId() : null;
        List<CommentResponse> data = documentCommentService.getReplies(commentId, userId);
        return ResponseEntity.ok(ApiResponse.success(data, "Replies"));
    }

    @PostMapping("/documents/{id}/comments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CommentResponse>> createComment(
            @PathVariable("id") UUID documentId,
            @Valid @RequestBody CreateCommentRequestDto request,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        UUID userId = currentUser.getUser().getId();
        CommentResponse data = documentCommentService.createComment(documentId, request.getBody(), userId);
        return ResponseEntity.ok(ApiResponse.success(data, "Comment created"));
    }

    @PostMapping("/comments/{id}/reply")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CommentResponse>> replyComment(
            @PathVariable("id") UUID parentCommentId,
            @Valid @RequestBody CreateCommentRequestDto request,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        UUID userId = currentUser.getUser().getId();
        CommentResponse data = documentCommentService.replyComment(parentCommentId, request.getBody(), userId);
        return ResponseEntity.ok(ApiResponse.success(data, "Reply created"));
    }

    @PostMapping("/comments/{id}/like")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CommentLikeToggleResponseDto>> toggleLike(
            @PathVariable("id") UUID commentId,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        UUID userId = currentUser.getUser().getId();
        CommentLikeToggleResponseDto data = documentCommentService.voteComment(commentId, userId, "UPVOTE");
        return ResponseEntity.ok(ApiResponse.success(data, "Like updated"));
    }

    @PostMapping("/comments/{id}/vote")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CommentLikeToggleResponseDto>> voteComment(
            @PathVariable("id") UUID commentId,
            @RequestParam(name = "type", defaultValue = "UPVOTE") String voteType,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        UUID userId = currentUser.getUser().getId();
        CommentLikeToggleResponseDto data = documentCommentService.voteComment(commentId, userId, voteType);
        return ResponseEntity.ok(ApiResponse.success(data, "Comment vote updated"));
    }

    @org.springframework.web.bind.annotation.PutMapping("/comments/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CommentResponse>> editComment(
            @PathVariable("id") UUID commentId,
            @Valid @RequestBody CreateCommentRequestDto request,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        UUID userId = currentUser.getUser().getId();
        CommentResponse data = documentCommentService.editComment(commentId, request.getBody(), userId);
        return ResponseEntity.ok(ApiResponse.success(data, "Comment updated"));
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/comments/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @PathVariable("id") UUID commentId,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        UUID userId = currentUser.getUser().getId();
        documentCommentService.deleteComment(commentId, userId);
        return ResponseEntity.ok(ApiResponse.success(null, "Comment deleted"));
    }

    @GetMapping("/comments/{id}/history")
    public ResponseEntity<ApiResponse<List<com.cmcu.itstudy.dto.document.CommentEditHistoryDto>>> getEditHistory(
            @PathVariable("id") UUID commentId
    ) {
        List<com.cmcu.itstudy.dto.document.CommentEditHistoryDto> data = documentCommentService.getEditHistory(commentId);
        return ResponseEntity.ok(ApiResponse.success(data, "Comment edit history"));
    }
}
