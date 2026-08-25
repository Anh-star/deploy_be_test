package com.cmcu.itstudy.controller;

import com.cmcu.itstudy.dto.admin.tag.AdminTagCreateRequestDto;
import com.cmcu.itstudy.dto.admin.tag.AdminTagPageResponseDto;
import com.cmcu.itstudy.dto.admin.tag.AdminTagResponseDto;
import com.cmcu.itstudy.dto.admin.tag.AdminTagStatusPatchRequestDto;
import com.cmcu.itstudy.dto.admin.tag.AdminTagUpdateRequestDto;
import com.cmcu.itstudy.dto.common.ApiResponse;
import com.cmcu.itstudy.service.contract.AdminTagService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/tags")
public class AdminTagController {

    private final AdminTagService adminTagService;

    public AdminTagController(AdminTagService adminTagService) {
        this.adminTagService = adminTagService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTENT_MODERATOR')")
    public ResponseEntity<ApiResponse<AdminTagPageResponseDto>> listTags(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        AdminTagPageResponseDto data = adminTagService.listTags(page, size);
        return ResponseEntity.ok(ApiResponse.success(data, "Tag list"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTENT_MODERATOR')")
    public ResponseEntity<ApiResponse<AdminTagResponseDto>> getTag(@PathVariable UUID id) {
        AdminTagResponseDto data = adminTagService.getTag(id);
        return ResponseEntity.ok(ApiResponse.success(data, "Tag detail"));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTENT_MODERATOR')")
    public ResponseEntity<ApiResponse<AdminTagResponseDto>> createTag(
            @Valid @RequestBody AdminTagCreateRequestDto request
    ) {
        AdminTagResponseDto data = adminTagService.createTag(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(data, "Tag created"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTENT_MODERATOR')")
    public ResponseEntity<ApiResponse<AdminTagResponseDto>> updateTag(
            @PathVariable UUID id,
            @Valid @RequestBody AdminTagUpdateRequestDto request
    ) {
        AdminTagResponseDto data = adminTagService.updateTag(id, request);
        return ResponseEntity.ok(ApiResponse.success(data, "Tag updated"));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTENT_MODERATOR')")
    public ResponseEntity<ApiResponse<AdminTagResponseDto>> patchStatus(
            @PathVariable UUID id,
            @Valid @RequestBody AdminTagStatusPatchRequestDto request
    ) {
        AdminTagResponseDto data = adminTagService.patchStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success(data, "Tag status updated"));
    }
}
