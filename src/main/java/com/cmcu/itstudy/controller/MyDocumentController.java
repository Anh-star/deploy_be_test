package com.cmcu.itstudy.controller;

import com.cmcu.itstudy.dto.common.ApiResponse;
import com.cmcu.itstudy.dto.document.DocumentCardDto;
import com.cmcu.itstudy.dto.document.DocumentCreateRequestDto;
import com.cmcu.itstudy.dto.document.DocumentUpdateRequestDto;
import com.cmcu.itstudy.dto.document.MyDocumentDetailDto;
import com.cmcu.itstudy.entity.User;
import com.cmcu.itstudy.security.UserDetailsImpl;
import com.cmcu.itstudy.service.contract.DocumentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/my-documents")
public class MyDocumentController {

    private final DocumentService documentService;

    public MyDocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DocumentCardDto>>> getMyDocuments(
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        User user = currentUser.getUser();
        List<DocumentCardDto> myDocuments = documentService.getMyDocuments(user);
        return ResponseEntity.ok(ApiResponse.success(myDocuments, "List of my documents"));
    }

    @GetMapping("/{documentId}")
    public ResponseEntity<ApiResponse<MyDocumentDetailDto>> getMyDocumentDetail(
            @PathVariable UUID documentId,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        User user = currentUser.getUser();
        MyDocumentDetailDto data = documentService.getMyDocumentDetail(documentId, user);
        return ResponseEntity.ok(ApiResponse.success(data, "My document detail"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DocumentCardDto>> createDocument(
            @Valid @RequestBody DocumentCreateRequestDto documentCreateRequestDto,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        User user = currentUser.getUser();
        DocumentCardDto createdDocument = documentService.createDocument(documentCreateRequestDto, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(createdDocument, "Document created successfully"));
    }

    @PutMapping("/{documentId}")
    public ResponseEntity<ApiResponse<DocumentCardDto>> updateDocument(
            @PathVariable UUID documentId,
            @Valid @RequestBody DocumentUpdateRequestDto documentUpdateRequestDto,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        User user = currentUser.getUser();
        DocumentCardDto updatedDocument = documentService.updateDocument(documentId, documentUpdateRequestDto, user);
        return ResponseEntity.ok(ApiResponse.success(updatedDocument, "Document updated successfully"));
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(
            @PathVariable UUID documentId,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        User user = currentUser.getUser();
        documentService.deleteDocument(documentId, user);
        return ResponseEntity.ok(ApiResponse.success(null, "Document deleted successfully"));
    }
}
