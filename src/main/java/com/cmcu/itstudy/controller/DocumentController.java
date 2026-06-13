package com.cmcu.itstudy.controller;

import com.cmcu.itstudy.dto.common.ApiResponse;
import com.cmcu.itstudy.dto.document.DocumentDetailResponseDto;
import com.cmcu.itstudy.dto.document.DocumentFileUrlResponseDto;
import com.cmcu.itstudy.dto.document.QuizListPageResponseDto;
import com.cmcu.itstudy.security.UserDetailsImpl;
import com.cmcu.itstudy.service.contract.DocumentQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api")
public class DocumentController {

    private final DocumentQueryService documentQueryService;

    public DocumentController(DocumentQueryService documentQueryService) {
        this.documentQueryService = documentQueryService;
    }

    @GetMapping("/documents/{id}")
public ResponseEntity<ApiResponse<DocumentDetailResponseDto>> getDocumentDetail(
        @PathVariable("id") UUID id,
        @AuthenticationPrincipal UserDetailsImpl currentUser
) {

    System.out.println("========== CONTROLLER ==========");
    System.out.println("CURRENT_USER = " + currentUser);

    UUID currentUserId =
            currentUser != null
                    ? currentUser.getUser().getId()
                    : null;

    System.out.println("CURRENT_USER_ID = " + currentUserId);
    System.out.println("================================");

    DocumentDetailResponseDto data =
            documentQueryService.getDocumentDetail(id, currentUserId);

    return ResponseEntity.ok(ApiResponse.success(data, "Document detail"));
} 

    @GetMapping("/documents/{id}/file")
    public ResponseEntity<ApiResponse<DocumentFileUrlResponseDto>> getDocumentPrimaryFileUrl(
            @PathVariable("id") UUID id
    ) {
        DocumentFileUrlResponseDto data = documentQueryService.getDocumentPrimaryFileUrl(id);
        return ResponseEntity.ok(ApiResponse.success(data, "Document file URL"));
    }

    @GetMapping("/documents/{documentId}/quizzes")
    public ResponseEntity<ApiResponse<QuizListPageResponseDto>> getDocumentQuizzes(
            @PathVariable("documentId") UUID documentId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "2") int size
    ) {
        QuizListPageResponseDto data = documentQueryService.getQuizzesByDocument(documentId, page, size);
        return ResponseEntity.ok(ApiResponse.success(data, "Document quizzes"));
    }
}
