package com.cmcu.itstudy.handle;

/**
 * Raised when an owner attempts to delete a READY AI-generated quiz whose
 * underlying {@code Quiz} already has at least one {@code QuizAttempt}.
 *
 * <p>Phase 6C: the contract rule is that we NEVER cascade-delete a Quiz
 * that anyone has already taken — quiz attempt history is immutable from
 * the contributor's perspective. Instead of cascading into
 * {@code tbl_quiz_attempts} / {@code tbl_quiz_attempt_answers}, the
 * service rejects the delete and the controller surfaces HTTP 409.
 *
 * <p>Maps to HTTP 409 Conflict via {@code GlobalExceptionHandler}.
 */
public class AutoQuizAlreadyHasAttemptsException extends RuntimeException {

    public AutoQuizAlreadyHasAttemptsException(String message) {
        super(message);
    }
}