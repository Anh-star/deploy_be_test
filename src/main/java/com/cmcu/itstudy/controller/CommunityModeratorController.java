package com.cmcu.itstudy.controller;

import com.cmcu.itstudy.dto.common.ApiResponse;
import com.cmcu.itstudy.dto.community.PostReportResponseDto;
import com.cmcu.itstudy.security.UserDetailsImpl;
import com.cmcu.itstudy.service.contract.CommunityPostService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/community/moderation")
@PreAuthorize("hasRole('COMMUNITY_MODERATOR') or hasRole('ADMIN')")
public class CommunityModeratorController {

    private final CommunityPostService communityPostService;

    public CommunityModeratorController(CommunityPostService communityPostService) {
        this.communityPostService = communityPostService;
    }

    @GetMapping("/reports")
    public ResponseEntity<ApiResponse<Page<PostReportResponseDto>>> getReportedPosts(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<PostReportResponseDto> reports = communityPostService.getReportedPosts(status, page, size);
        return ResponseEntity.ok(ApiResponse.success(reports, "Lấy danh sách báo cáo bài viết thành công"));
    }

    @PutMapping("/reports/{reportId}/resolve")
    public ResponseEntity<ApiResponse<Void>> resolveReport(
            @PathVariable UUID reportId,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        communityPostService.resolveReport(reportId, currentUser.getUser().getId());
        return ResponseEntity.ok(ApiResponse.success(null, "Đã đánh dấu xử lý báo cáo"));
    }

    @PutMapping("/reports/{reportId}/dismiss")
    public ResponseEntity<ApiResponse<Void>> dismissReport(
            @PathVariable UUID reportId,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        communityPostService.dismissReport(reportId, currentUser.getUser().getId());
        return ResponseEntity.ok(ApiResponse.success(null, "Đã bỏ qua báo cáo"));
    }

    @PutMapping("/posts/{postId}/hide")
    public ResponseEntity<ApiResponse<Void>> hidePost(
            @PathVariable UUID postId,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        communityPostService.hidePost(postId, currentUser.getUser().getId());
        return ResponseEntity.ok(ApiResponse.success(null, "Đã ẩn bài viết"));
    }

    @PutMapping("/posts/{postId}/unhide")
    public ResponseEntity<ApiResponse<Void>> unhidePost(
            @PathVariable UUID postId,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        communityPostService.unhidePost(postId, currentUser.getUser().getId());
        return ResponseEntity.ok(ApiResponse.success(null, "Đã hiện lại bài viết"));
    }

    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<ApiResponse<Void>> moderatorDeletePost(
            @PathVariable UUID postId,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        communityPostService.moderatorDeletePost(postId, currentUser.getUser().getId());
        return ResponseEntity.ok(ApiResponse.success(null, "Đã xóa bài viết"));
    }
}
