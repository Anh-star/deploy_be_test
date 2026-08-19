package com.cmcu.itstudy.controller;

import com.cmcu.itstudy.dto.common.ApiResponse;
import com.cmcu.itstudy.dto.community.CommunityModerationStatsDto;
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
@PreAuthorize("hasAnyRole('COMMUNITY_MODERATOR', 'ADMIN')")
public class CommunityModeratorController {

    private final CommunityPostService communityPostService;

    public CommunityModeratorController(CommunityPostService communityPostService) {
        this.communityPostService = communityPostService;
    }

    private UUID getUserId(UserDetailsImpl currentUser) {
        return (currentUser != null && currentUser.getUser() != null) ? currentUser.getUser().getId() : null;
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<CommunityModerationStatsDto>> getModerationStats() {
        CommunityModerationStatsDto data = communityPostService.getModerationStats();
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy thống kê kiểm duyệt thành công"));
    }

    @GetMapping("/reports")
    public ResponseEntity<ApiResponse<Page<PostReportResponseDto>>> getReportedPosts(
            @RequestParam(required = false, defaultValue = "PENDING") String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<PostReportResponseDto> data = communityPostService.getReportedPosts(status, keyword, startDate, endDate, page, size);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy danh sách báo cáo thành công"));
    }

    @PutMapping("/reports/{reportId}/resolve")
    public ResponseEntity<ApiResponse<Void>> resolveReport(
            @PathVariable UUID reportId,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        communityPostService.resolveReport(reportId, getUserId(currentUser));
        return ResponseEntity.ok(ApiResponse.success(null, "Đã xử lý báo cáo"));
    }

    @PutMapping("/reports/{reportId}/dismiss")
    public ResponseEntity<ApiResponse<Void>> dismissReport(
            @PathVariable UUID reportId,
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        communityPostService.dismissReport(reportId, getUserId(currentUser), reason);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã bỏ qua báo cáo"));
    }

    @PutMapping("/posts/{postId}/dismiss-reports")
    public ResponseEntity<ApiResponse<Void>> dismissReportByPostId(
            @PathVariable UUID postId,
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        communityPostService.dismissReportByPostId(postId, getUserId(currentUser), reason);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã bỏ qua các báo cáo của bài viết"));
    }

    @PutMapping("/posts/{postId}/hide")
    public ResponseEntity<ApiResponse<Void>> hidePost(
            @PathVariable UUID postId,
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        communityPostService.hidePost(postId, getUserId(currentUser), reason);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã ẩn bài viết"));
    }

    @PutMapping("/posts/{postId}/unhide")
    public ResponseEntity<ApiResponse<Void>> unhidePost(
            @PathVariable UUID postId,
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        communityPostService.unhidePost(postId, getUserId(currentUser), reason);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã hiện lại bài viết"));
    }

    @PutMapping("/reports/{reportId}/escalate")
    public ResponseEntity<ApiResponse<Void>> escalateReport(
            @PathVariable UUID reportId,
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        communityPostService.escalateReport(reportId, getUserId(currentUser), reason);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã chuyển báo cáo lên Ban Quản Trị (Admin)"));
    }

    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<ApiResponse<Void>> moderatorDeletePost(
            @PathVariable UUID postId,
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        communityPostService.moderatorDeletePost(postId, getUserId(currentUser), reason);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã xóa bài viết"));
    }
}
