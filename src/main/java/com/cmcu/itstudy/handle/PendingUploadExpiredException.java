package com.cmcu.itstudy.handle;

/**
 * Thrown when a paid-create request references an {@code uploadId}
 * whose bind deadline has already passed.
 *
 * <p>HTTP semantics: 409 (conflict, business condition).
 */
public class PendingUploadExpiredException extends RuntimeException {

    public PendingUploadExpiredException(String message) {
        super(message);
    }
}