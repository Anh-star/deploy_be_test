package com.cmcu.itstudy.handle;

/**
 * Raised when an owner attempts to delete a {@code QuizGeneration} row
 * whose status is not yet terminal — i.e. {@code WAITING_SOURCE},
 * {@code QUEUED}, or {@code PROCESSING}.
 *
 * <p>Phase 6C: deleting an in-flight generation would race with the
 * dispatcher / callback paths and could leave the document in an
 * unrecoverable state. The owner must wait for the generation to reach
 * {@code READY}, {@code FAILED}, or {@code CANCELLED} before deleting.
 *
 * <p>Maps to HTTP 409 Conflict via {@code GlobalExceptionHandler}.
 */
public class AutoQuizGenerationNotInTerminalStateException extends RuntimeException {

    public AutoQuizGenerationNotInTerminalStateException(String message) {
        super(message);
    }
}