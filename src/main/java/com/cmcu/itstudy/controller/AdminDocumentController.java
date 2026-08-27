package com.cmcu.itstudy.controller;

import com.cmcu.itstudy.dto.admin.document.AdminPendingDocumentsPageResponseDto;
import com.cmcu.itstudy.dto.admin.document.DocumentAdminDetailDto;
import com.cmcu.itstudy.dto.admin.document.DocumentAdminStatusPatchRequestDto;
import com.cmcu.itstudy.dto.common.ApiResponse;
import com.cmcu.itstudy.dto.common.MessageResponseDto;
import com.cmcu.itstudy.dto.document.DocumentPreviewStatusDto;
import com.cmcu.itstudy.entity.User;
import com.cmcu.itstudy.security.UserDetailsImpl;
import com.cmcu.itstudy.service.contract.AdminDocumentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cmcu.itstudy.dto.document.DocumentReportResponseDto;
import com.cmcu.itstudy.service.contract.DocumentService;
import org.springframework.data.domain.Page;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/documents")
public class AdminDocumentController {

    private final AdminDocumentService adminDocumentService;
    private final DocumentService documentService;

    public AdminDocumentController(AdminDocumentService adminDocumentService, DocumentService documentService) {
        this.adminDocumentService = adminDocumentService;
        this.documentService = documentService;
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTENT_MODERATOR', 'USER_MODERATOR')")
    public ResponseEntity<ApiResponse<AdminPendingDocumentsPageResponseDto>> listPending(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        java.time.LocalDateTime start = null;
        if (org.springframework.util.StringUtils.hasText(startDate)) {
            try {
                start = java.time.LocalDate.parse(startDate.trim()).atStartOfDay();
            } catch (Exception e) {
                try {
                    start = java.time.LocalDateTime.parse(startDate.trim());
                } catch (Exception ignored) {}
            }
        }
        java.time.LocalDateTime end = null;
        if (org.springframework.util.StringUtils.hasText(endDate)) {
            try {
                end = java.time.LocalDate.parse(endDate.trim()).atTime(java.time.LocalTime.MAX);
            } catch (Exception e) {
                try {
                    end = java.time.LocalDateTime.parse(endDate.trim());
                } catch (Exception ignored) {}
            }
        }
        AdminPendingDocumentsPageResponseDto data = adminDocumentService.listPendingDocuments(status, search, start, end, page, size);
        return ResponseEntity.ok(ApiResponse.success(data, "Pending documents"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTENT_MODERATOR', 'USER_MODERATOR')")
    public ResponseEntity<ApiResponse<DocumentAdminDetailDto>> getDocumentDetail(@PathVariable("id") UUID id) {
        DocumentAdminDetailDto data = adminDocumentService.getDocumentDetail(id);
        return ResponseEntity.ok(ApiResponse.success(data, "Document detail"));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTENT_MODERATOR', 'USER_MODERATOR')")
    public ResponseEntity<ApiResponse<MessageResponseDto>> patchStatus(
            @PathVariable("id") UUID id,
            @Valid @RequestBody DocumentAdminStatusPatchRequestDto request,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        User moderator = currentUser.getUser();
        adminDocumentService.updateDocumentStatus(id, request, moderator);
        return ResponseEntity.ok(ApiResponse.success(
                MessageResponseDto.builder().message("Document status updated").build(),
                "OK"
        ));
    }

    @GetMapping("/reports")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTENT_MODERATOR', 'USER_MODERATOR')")
    public ResponseEntity<ApiResponse<com.cmcu.itstudy.dto.document.DocumentReportPageResponseDto>> getReportedDocuments(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        java.time.LocalDateTime start = null;
        if (org.springframework.util.StringUtils.hasText(startDate)) {
            try {
                start = java.time.LocalDate.parse(startDate.trim()).atStartOfDay();
            } catch (Exception e) {
                try {
                    start = java.time.LocalDateTime.parse(startDate.trim());
                } catch (Exception ignored) {}
            }
        }
        java.time.LocalDateTime end = null;
        if (org.springframework.util.StringUtils.hasText(endDate)) {
            try {
                end = java.time.LocalDate.parse(endDate.trim()).atTime(java.time.LocalTime.MAX);
            } catch (Exception e) {
                try {
                    end = java.time.LocalDateTime.parse(endDate.trim());
                } catch (Exception ignored) {}
            }
        }
        com.cmcu.itstudy.dto.document.DocumentReportPageResponseDto data =
                documentService.getReportedDocuments(status, search, start, end, page, size);
        return ResponseEntity.ok(ApiResponse.success(data, "Reported documents"));
    }

    @PatchMapping("/reports/{reportId}/resolve")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTENT_MODERATOR', 'USER_MODERATOR')")
    public ResponseEntity<ApiResponse<MessageResponseDto>> resolveReport(
            @PathVariable("reportId") UUID reportId,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        User resolver = currentUser.getUser();
        documentService.resolveReport(reportId, resolver);
        return ResponseEntity.ok(ApiResponse.success(
                MessageResponseDto.builder().message("Đã xử lý báo cáo thành công").build(),
                "OK"
        ));
    }

    @PatchMapping("/reports/{reportId}/dismiss")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTENT_MODERATOR', 'USER_MODERATOR')")
    public ResponseEntity<ApiResponse<MessageResponseDto>> dismissReport(
            @PathVariable("reportId") UUID reportId,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        User resolver = currentUser.getUser();
        documentService.dismissReport(reportId, resolver);
        return ResponseEntity.ok(ApiResponse.success(
                MessageResponseDto.builder().message("Đã bỏ qua báo cáo").build(),
                "OK"
        ));
    }

    /**
     * Returns the async Office-to-PDF preview status for a document.
     * Used by the frontend moderator review page to decide when to enable
     * the approve button for Office documents.
     *
     * <p>The response identifies whether the document is an Office file
     * and, if so, what the current worker-managed FULL artifact status is.
     * Supabase storage paths and credentials are never exposed.</p>
     */
    @GetMapping("/{id}/preview-status")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTENT_MODERATOR', 'USER_MODERATOR')")
    public ResponseEntity<ApiResponse<DocumentPreviewStatusDto>> getDocumentPreviewStatus(
            @PathVariable("id") UUID id
    ) {
        DocumentPreviewStatusDto data = adminDocumentService.getDocumentPreviewStatus(id);
        return ResponseEntity.ok(ApiResponse.success(data, "Document preview status"));
    }
}
