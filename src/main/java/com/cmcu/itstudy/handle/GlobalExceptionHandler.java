package com.cmcu.itstudy.handle;

import com.cmcu.itstudy.dto.common.ApiResponse;
import com.cmcu.itstudy.handle.AIGeneratedQuizValidationException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.NoSuchElementException;
import java.util.stream.Collectors;

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

    @ExceptionHandler(AIGeneratedQuizValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAIGeneratedQuizValidation(AIGeneratedQuizValidationException ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : "Invalid AI-generated quiz data";
        ApiResponse<Void> body = ApiResponse.failure(message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String paramName = ex.getName();
        String message = "Invalid value for parameter: " + paramName;
        ApiResponse<Void> body = ApiResponse.failure(message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {

    ex.printStackTrace();

    String msg = ex.getClass().getName() + ": " + ex.getMessage();

    ApiResponse<Void> body =
            ApiResponse.failure(msg);

    return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(body);
}
}

