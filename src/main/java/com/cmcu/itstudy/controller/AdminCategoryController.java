package com.cmcu.itstudy.controller;

import com.cmcu.itstudy.dto.admin.category.AdminCategoryCreateRequestDto;
import com.cmcu.itstudy.dto.admin.category.AdminCategoryPageResponseDto;
import com.cmcu.itstudy.dto.admin.category.AdminCategoryResponseDto;
import com.cmcu.itstudy.dto.admin.category.AdminCategoryStatusPatchRequestDto;
import com.cmcu.itstudy.dto.admin.category.AdminCategoryUpdateRequestDto;
import com.cmcu.itstudy.dto.common.ApiResponse;
import com.cmcu.itstudy.service.contract.AdminCategoryService;
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
@RequestMapping("/api/admin/categories")
public class AdminCategoryController {

    private final AdminCategoryService adminCategoryService;

    public AdminCategoryController(AdminCategoryService adminCategoryService) {
        this.adminCategoryService = adminCategoryService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTENT_MODERATOR')")
    public ResponseEntity<ApiResponse<AdminCategoryPageResponseDto>> listCategories(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        AdminCategoryPageResponseDto data = adminCategoryService.listCategories(page, size);
        return ResponseEntity.ok(ApiResponse.success(data, "Category list"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTENT_MODERATOR')")
    public ResponseEntity<ApiResponse<AdminCategoryResponseDto>> getCategory(@PathVariable UUID id) {
        AdminCategoryResponseDto data = adminCategoryService.getCategory(id);
        return ResponseEntity.ok(ApiResponse.success(data, "Category detail"));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTENT_MODERATOR')")
    public ResponseEntity<ApiResponse<AdminCategoryResponseDto>> createCategory(
            @Valid @RequestBody AdminCategoryCreateRequestDto request
    ) {
        AdminCategoryResponseDto data = adminCategoryService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(data, "Category created"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTENT_MODERATOR')")
    public ResponseEntity<ApiResponse<AdminCategoryResponseDto>> updateCategory(
            @PathVariable UUID id,
            @Valid @RequestBody AdminCategoryUpdateRequestDto request
    ) {
        AdminCategoryResponseDto data = adminCategoryService.updateCategory(id, request);
        return ResponseEntity.ok(ApiResponse.success(data, "Category updated"));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTENT_MODERATOR')")
    public ResponseEntity<ApiResponse<AdminCategoryResponseDto>> patchStatus(
            @PathVariable UUID id,
            @Valid @RequestBody AdminCategoryStatusPatchRequestDto request
    ) {
        AdminCategoryResponseDto data = adminCategoryService.patchStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success(data, "Category status updated"));
    }
}
