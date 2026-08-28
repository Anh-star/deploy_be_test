package com.cmcu.itstudy.controller;

import com.cmcu.itstudy.dto.common.ApiResponse;
import com.cmcu.itstudy.dto.document.DocumentCardDto;
import com.cmcu.itstudy.dto.document.DocumentCreateRequestDto;
import com.cmcu.itstudy.dto.document.DocumentUpdateRequestDto;
import com.cmcu.itstudy.dto.document.MyDocumentAutoQuizCreateRequestDto;
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
import com.cmcu.itstudy.service.contract.QuizGenerationService;
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
    private final QuizGenerationService quizGenerationService;

    public MyDocumentController(
            DocumentService documentService,
            PaidUploadTargetOrchestrator paidUploadTargetOrchestrator,
            DocumentCommandRouter documentCommandRouter,
            QuizGenerationService quizGenerationService) {
        this.documentService = documentService;
        this.paidUploadTargetOrchestrator = paidUploadTargetOrchestrator;
        this.documentCommandRouter = documentCommandRouter;
        this.quizGenerationService = quizGenerationService;
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

    /**
     * Phase Multi Auto Quiz 2 — return every AI quiz generation for the
     * supplied document, newest-first. The document owner can call this
     * regardless of how many generations exist.
     */
    @GetMapping("/{documentId}/auto-quizzes")
    public ResponseEntity<ApiResponse<List<MyDocumentAutoQuizDto>>> getMyDocumentAutoQuizzes(
            @PathVariable UUID documentId,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        User user = currentUser.getUser();
        List<MyDocumentAutoQuizDto> data =
                documentService.getMyDocumentAutoQuizzes(documentId, user);
        return ResponseEntity.ok(ApiResponse.success(data, "All auto quizzes for this document"));
    }

    /**
     * Phase Multi Auto Quiz 2 — enqueue a brand-new AI quiz generation for
     * the supplied document. Each call creates an independent generation;
     * there is no limit and no reuse of a previous generation.
     */
    @PostMapping("/{documentId}/auto-quizzes")
    public ResponseEntity<ApiResponse<MyDocumentAutoQuizDto>> createMyDocumentAutoQuiz(
            @PathVariable UUID documentId,
            @Valid @RequestBody MyDocumentAutoQuizCreateRequestDto request,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        User user = currentUser.getUser();
        MyDocumentAutoQuizDto created =
                documentService.createMyDocumentAutoQuiz(documentId, request, user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Auto quiz generation queued"));
    }

    /**
     * Phase 6C — owner-initiated delete of a single auto-quiz
     * generation row, including its associated Quiz when the generation
     * is {@code READY}.
     *
     * <p>The endpoint is owner-authenticated only
     * (Spring Security's {@code .anyRequest().authenticated()} already
     * covers this route — NO {@code permitAll}, NO dispatch-token
     * header). The business authorisation (caller must be the document
     * owner) is enforced inside
     * {@link QuizGenerationService#deleteForOwner}.</p>
     *
     * <p>Status mapping mirrors the rest of the controller's mutation
     * endpoints — 200 + {@code ApiResponse.success(null, ...)} on
     * success to keep client handling symmetric with the create /
     * update paths.</p>
     *
     * <p>Error contract (handled by {@code GlobalExceptionHandler}):</p>
     * <ul>
     *   <li>{@code NoSuchElementException} → 404</li>
     *   <li>{@code SecurityException} → 500 (pre-existing convention;
     *       the controller surface mirrors {@code updateDocument} and
     *       {@code deleteDocument})</li>
     *   <li>{@code AutoQuizGenerationNotInTerminalStateException}
     *       → 409 (WAITING_SOURCE / QUEUED / PROCESSING)</li>
     *   <li>{@code AutoQuizAlreadyHasAttemptsException} → 409 (READY
     *       with at least one QuizAttempt)</li>
     * </ul>
     */
    @DeleteMapping("/{documentId}/auto-quizzes/{generationId}")
    public ResponseEntity<ApiResponse<Void>> deleteMyDocumentAutoQuiz(
            @PathVariable UUID documentId,
            @PathVariable UUID generationId,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        User user = currentUser.getUser();
        quizGenerationService.deleteForOwner(documentId, generationId, user);
        return ResponseEntity.ok(ApiResponse.success(null, "Auto quiz deleted successfully"));
    }

    @GetMapping("/quizzes")
    public ResponseEntity<ApiResponse<MyDocumentQuizListDto>> getMyDocumentQuizzes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        if (currentUser == null || currentUser.getUser() == null) {
            throw new org.springframework.security.access.AccessDeniedException("Vui lòng đăng nhập để xem danh sách bài kiểm tra");
        }
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
