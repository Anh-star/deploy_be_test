package com.cmcu.itstudy.handle;

import com.cmcu.itstudy.dto.common.ApiResponse;
import com.cmcu.itstudy.handle.AIGeneratedQuizValidationException;
import com.cmcu.itstudy.handle.WithdrawalStateConflictException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));

        ApiResponse<Void> body = ApiResponse.failure(message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        ApiResponse<Void> body = ApiResponse.failure(ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException ex) {
        ApiResponse<Void> body = ApiResponse.failure("Unauthorized");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException ex) {
        ApiResponse<Void> body = ApiResponse.failure("Forbidden");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoSuchElementException(NoSuchElementException ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : "Resource not found";
        ApiResponse<Void> body = ApiResponse.failure(message);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalStateException(IllegalStateException ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : "Invalid state";
        ApiResponse<Void> body = ApiResponse.failure(message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException ex) {
        // Login/refresh/... are currently throwing IllegalArgumentException for invalid credentials/tokens.
        String message = ex.getMessage();
        HttpStatus status;

        if (message != null && (message.contains("Invalid credentials")
                || message.contains("Invalid email or password")
                || message.contains("Refresh token")
                || message.contains("reset token"))) {
            status = HttpStatus.UNAUTHORIZED;
        } else {
            status = HttpStatus.BAD_REQUEST;
        }

        ApiResponse<Void> body = ApiResponse.failure(message);
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(QuizAlreadySubmittedException.class)
    public ResponseEntity<ApiResponse<Void>> handleQuizAlreadySubmitted(QuizAlreadySubmittedException ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : "Attempt already submitted";
        ApiResponse<Void> body = ApiResponse.failure(message);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(DocumentPricingLockedException.class)
    public ResponseEntity<ApiResponse<Void>> handleDocumentPricingLocked(DocumentPricingLockedException ex) {
        String message = ex.getMessage() != null
                ? ex.getMessage()
                : "Tài liệu đã có người mua nên không thể thay đổi hình thức hoặc giá bán.";
        ApiResponse<Void> body = ApiResponse.failure(message);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(PreviewNotReadyException.class)
    public ResponseEntity<ApiResponse<Void>> handlePreviewNotReady(PreviewNotReadyException ex) {
        String message = ex.getMessage() != null
                ? ex.getMessage()
                : "Bản xem trước DOC/DOCX chưa sẵn sàng để phê duyệt.";
        ApiResponse<Void> body = ApiResponse.failure(message);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(AIGeneratedQuizValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAIGeneratedQuizValidation(AIGeneratedQuizValidationException ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : "Invalid AI-generated quiz data";
        ApiResponse<Void> body = ApiResponse.failure(message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(InvalidFileNameException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidFileName(InvalidFileNameException ex) {
        String safeMessage = ex.getMessage() != null ? ex.getMessage() : "Invalid file name";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure(safeMessage));
    }

    @ExceptionHandler(UnsupportedFileTypeException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedFileType(UnsupportedFileTypeException ex) {
        String safeMessage = ex.getMessage() != null ? ex.getMessage() : "Unsupported file type";
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.failure(safeMessage));
    }

    @ExceptionHandler(MimeExtensionMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMimeExtensionMismatch(MimeExtensionMismatchException ex) {
        String safeMessage = ex.getMessage() != null ? ex.getMessage() : "File extension and MIME type do not match";
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.failure(safeMessage));
    }

    @ExceptionHandler(InvalidFileSizeException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidFileSize(InvalidFileSizeException ex) {
        String safeMessage = ex.getMessage() != null ? ex.getMessage() : "Invalid file size";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure(safeMessage));
    }

    @ExceptionHandler(FileTooLargeException.class)
    public ResponseEntity<ApiResponse<Void>> handleFileTooLarge(FileTooLargeException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.failure("File is too large"));
    }

    @ExceptionHandler(PrivateBucketNotConfiguredException.class)
    public ResponseEntity<ApiResponse<Void>> handlePrivateBucketNotConfigured(PrivateBucketNotConfiguredException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.failure(
                        "Paid document storage is not configured on the server"));
    }

    @ExceptionHandler(PreviewFileTooLargeException.class)
    public ResponseEntity<ApiResponse<Void>> handlePreviewFileTooLarge(PreviewFileTooLargeException ex) {
        // Never echo size / bucket / path. Generic message only.
        log.warn("Preview file exceeded size cap");
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.failure("Preview file is too large"));
    }

    @ExceptionHandler(PaidPreviewUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handlePaidPreviewUnavailable(PaidPreviewUnavailableException ex) {
        // The preview controller catches this first and translates it
        // into a 200 LOCKED JSON payload; this handler is only here as
        // a safety net for callers that bypass the controller (e.g.
        // direct service usage from tests or future endpoints).
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.failure("Paid preview is not available"));
    }

    @ExceptionHandler(SignedUploadTargetFailedException.class)
    public ResponseEntity<ApiResponse<Void>> handleSignedUploadTargetFailed(SignedUploadTargetFailedException ex) {
        // Never echo Supabase details back to the caller.
        log.warn("Signed upload target failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.failure("Failed to create upload target"));
    }

    @ExceptionHandler(StorageObjectNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleStorageObjectNotFound(StorageObjectNotFoundException ex) {
        // 400 with a single safe string — never echo path / uploadId.
        log.warn("Storage object not found");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure("Uploaded file is not available"));
    }

    @ExceptionHandler(PendingUploadNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handlePendingUploadNotFound(PendingUploadNotFoundException ex) {
        log.warn("Pending upload not found");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure("Upload identifier is not valid"));
    }

    @ExceptionHandler(PendingUploadNotOwnedException.class)
    public ResponseEntity<ApiResponse<Void>> handlePendingUploadNotOwned(PendingUploadNotOwnedException ex) {
        // 403 forbidden. The message is intentionally generic.
        log.warn("Pending upload not owned by current user");
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.failure("Upload does not belong to current user"));
    }

    @ExceptionHandler(PendingUploadExpiredException.class)
    public ResponseEntity<ApiResponse<Void>> handlePendingUploadExpired(PendingUploadExpiredException ex) {
        log.warn("Pending upload expired: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.failure("Upload bind deadline has passed"));
    }

    @ExceptionHandler(PendingUploadAlreadyBoundException.class)
    public ResponseEntity<ApiResponse<Void>> handlePendingUploadAlreadyBound(PendingUploadAlreadyBoundException ex) {
        log.warn("Pending upload already bound: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.failure("Upload has already been bound"));
    }

    @ExceptionHandler(PendingUploadBindConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handlePendingUploadBindConflict(PendingUploadBindConflictException ex) {
        log.warn("Pending upload bind conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.failure("Upload binding conflict"));
    }

    @ExceptionHandler(StorageObjectSizeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleStorageObjectSizeMismatch(StorageObjectSizeMismatchException ex) {
        log.warn("Storage object size mismatch: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.failure("Uploaded file size does not match declared size"));
    }

    @ExceptionHandler(StorageObjectMimeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleStorageObjectMimeMismatch(StorageObjectMimeMismatchException ex) {
        log.warn("Storage object MIME mismatch: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.failure("Uploaded file type does not match declared MIME type"));
    }

    @ExceptionHandler(PendingUploadRegistrationFailedException.class)
    public ResponseEntity<ApiResponse<Void>> handlePendingUploadRegistrationFailed(PendingUploadRegistrationFailedException ex) {
        // Never echo underlying DB details.
        log.error("Pending upload registration failed");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure("Failed to register upload"));
    }

    @ExceptionHandler(WithdrawalIdempotencyConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleWithdrawalIdempotencyConflict(WithdrawalIdempotencyConflictException ex) {
        String message = ex.getMessage() != null
                ? ex.getMessage()
                : "Client request ID was already used with different withdrawal data";
        ApiResponse<Void> body = ApiResponse.failure(message);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(WithdrawalStateConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleWithdrawalStateConflict(WithdrawalStateConflictException ex) {
        String message = ex.getMessage() != null
                ? ex.getMessage()
                : "Withdrawal request has already been processed";
        ApiResponse<Void> body = ApiResponse.failure(message);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String paramName = ex.getName();
        String message = "Invalid value for parameter: " + paramName;
        ApiResponse<Void> body = ApiResponse.failure(message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Translate Jackson / Spring request-body conversion failures
     * (malformed JSON, missing JSON creator, type mismatch inside the
     * body, ...) into HTTP {@code 400 Bad Request}.
     *
     * <p>Without this handler, {@link HttpMessageNotReadableException}
     * falls through to the generic {@link Exception} handler and the
     * client receives a {@code 500 Internal Server Error} for what is
     * actually a malformed request — a contract bug.
     *
     * <p>The response message is intentionally generic
     * ({@code "Invalid request body"}). The original Jackson error
     * message is logged at {@code WARN} for diagnostics but is NOT
     * echoed back to the caller, so we never leak DTO class names,
     * stack traces, raw request bodies, secrets, tokens, or Supabase
     * URLs.
     *
     * <p>Note: {@link HttpMessageNotReadableException} extends
     * {@code HttpMessageConversionException}, so handling the concrete
     * subclass here is sufficient and avoids creating an ambiguous
     * twin handler.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        // Surface only the cause category, never the full message.
        Throwable cause = ex.getMostSpecificCause();
        String causeCategory = cause != null ? cause.getClass().getSimpleName() : "unknown";
        log.warn("Rejected malformed request body: cause={}", causeCategory);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure("Invalid request body"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unhandled exception", ex);

        ApiResponse<Void> body = ApiResponse.failure("Internal server error");

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body);
    }
}

