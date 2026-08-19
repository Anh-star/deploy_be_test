package com.cmcu.itstudy.controller;

import com.cmcu.itstudy.dto.common.ApiResponse;
import com.cmcu.itstudy.dto.community.PostReportResponseDto;
import com.cmcu.itstudy.security.UserDetailsImpl;
import com.cmcu.itstudy.service.contract.CommunityPostService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/community-moderation")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCommunityModerationController {

    private final CommunityPostService communityPostService;

    public AdminCommunityModerationController(CommunityPostService communityPostService) {
        this.communityPostService = communityPostService;
    }

    private UUID getUserId(UserDetailsImpl currentUser) {
        return (currentUser != null && currentUser.getUser() != null) ? currentUser.getUser().getId() : null;
    }

    @GetMapping("/reports")
    public ResponseEntity<ApiResponse<Page<PostReportResponseDto>>> getEscalatedReports(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<PostReportResponseDto> data = communityPostService.getEscalatedReports(keyword, startDate, endDate, page, size);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy danh sách báo cáo chuyển tiếp thành công"));
    }

    @PutMapping("/reports/{reportId}/ban-user")
    public ResponseEntity<ApiResponse<Void>> banUser(
            @PathVariable UUID reportId,
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        communityPostService.adminBanUserFromReport(reportId, getUserId(currentUser), reason);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã khóa tài khoản người dùng và ẩn toàn bộ bài viết, tài liệu liên quan"));
    }

    @PutMapping("/reports/{reportId}/dismiss")
    public ResponseEntity<ApiResponse<Void>> dismissReport(
            @PathVariable UUID reportId,
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        communityPostService.adminAcquitReport(reportId, getUserId(currentUser), reason);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã bỏ qua báo cáo và hiển thị lại bài viết"));
    }
}
