package com.cmcu.itstudy.handle;

/**
 * Raised when a moderator attempts to approve a DOC/DOCX document
 * whose FULL preview artifact is not yet READY.
 *
 * <p>Maps to HTTP 409 Conflict via {@code GlobalExceptionHandler}.
 *
 * <p>The message is the safe, user-facing Vietnamese text shown to the
 * moderator. Internal conversion errors, storage paths, and stack traces
 * are never surfaced in this exception.
 */
public class PreviewNotReadyException extends RuntimeException {

    public PreviewNotReadyException(String message) {
        super(message);
    }
}
