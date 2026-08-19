package com.cmcu.itstudy.controller;

import com.cmcu.itstudy.dto.common.ApiResponse;
import com.cmcu.itstudy.dto.document.DocumentCardDto;
import com.cmcu.itstudy.dto.document.DocumentCreateRequestDto;
import com.cmcu.itstudy.dto.document.DocumentUpdateRequestDto;
import com.cmcu.itstudy.dto.document.MyDocumentAutoQuizDto;
import com.cmcu.itstudy.dto.document.MyDocumentDetailDto;
import com.cmcu.itstudy.dto.document.MyDocumentQuizListDto;
import com.cmcu.itstudy.dto.storage.PaidUploadTargetRequestDto;
import com.cmcu.itstudy.dto.storage.PaidUploadTargetResponseDto;
import com.cmcu.itstudy.entity.User;
import com.cmcu.itstudy.security.UserDetailsImpl;
import com.cmcu.itstudy.service.contract.DocumentCommandRouter;
import com.cmcu.itstudy.service.contract.DocumentService;
import com.cmcu.itstudy.service.contract.PaidUploadTargetOrchestrator;
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
    private final PaidUploadTargetOrchestrator paidUploadTargetOrchestrator;
    private final DocumentCommandRouter documentCommandRouter;

    public MyDocumentController(
            DocumentService documentService,
            PaidUploadTargetOrchestrator paidUploadTargetOrchestrator,
            DocumentCommandRouter documentCommandRouter) {
        this.documentService = documentService;
        this.paidUploadTargetOrchestrator = paidUploadTargetOrchestrator;
        this.documentCommandRouter = documentCommandRouter;
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

    @GetMapping("/{documentId}/auto-quiz")
    public ResponseEntity<ApiResponse<MyDocumentAutoQuizDto>> getMyDocumentAutoQuiz(
            @PathVariable UUID documentId,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        User user = currentUser.getUser();
        MyDocumentAutoQuizDto data = documentService.getMyDocumentAutoQuiz(documentId, user);
        return ResponseEntity.ok(ApiResponse.success(data, "Auto quiz info"));
    }

    @GetMapping("/quizzes")
    public ResponseEntity<ApiResponse<MyDocumentQuizListDto>> getMyDocumentQuizzes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        User user = currentUser.getUser();
        MyDocumentQuizListDto data = documentService.getMyDocumentQuizzes(page, size, user);
        return ResponseEntity.ok(ApiResponse.success(data, "My document quizzes"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DocumentCardDto>> createDocument(
            @Valid @RequestBody DocumentCreateRequestDto documentCreateRequestDto,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        User user = currentUser.getUser();
        DocumentCardDto createdDocument = documentCommandRouter.routeCreate(
                documentCreateRequestDto, user);
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

    /**
     * Create a Supabase signed upload target for a paid document.
     *
     * <p>Accepts only metadata (filename, MIME, size). The bucket, object
     * path, and userId are server-resolved; the endpoint does NOT accept
     * them from the request.
     *
     * <p>This endpoint does not upload the file binary and does not create
     * a Document row; it only creates the {@code PendingStorageUpload}
     * server-side row.
     */
    @PostMapping("/storage/paid-upload-target")
    public ResponseEntity<ApiResponse<PaidUploadTargetResponseDto>> createPaidUploadTarget(
            @Valid @RequestBody PaidUploadTargetRequestDto request,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        User user = currentUser.getUser();
        PaidUploadTargetResponseDto response =
                paidUploadTargetOrchestrator.createTarget(user, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Paid upload target created"));
    }
}
