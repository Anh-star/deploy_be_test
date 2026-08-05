package com.cmcu.itstudy.controller;

import com.cmcu.itstudy.dto.admin.document.AdminPendingDocumentsPageResponseDto;
import com.cmcu.itstudy.dto.admin.document.DocumentAdminDetailDto;
import com.cmcu.itstudy.dto.admin.document.DocumentAdminStatusPatchRequestDto;
import com.cmcu.itstudy.dto.common.ApiResponse;
import com.cmcu.itstudy.dto.common.MessageResponseDto;
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
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        AdminPendingDocumentsPageResponseDto data = adminDocumentService.listPendingDocuments(page, size);
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
    public ResponseEntity<ApiResponse<Page<DocumentReportResponseDto>>> getReportedDocuments(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<DocumentReportResponseDto> data = documentService.getReportedDocuments(status, page, size);
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
}
