package com.cmcu.itstudy.handle;

/**
 * Thrown when a paid-create request references an {@code uploadId}
 * that belongs to a different user.
 *
 * <p>HTTP semantics: 403. We deliberately do NOT leak the uploadId in
 * the public message; the server-side log records the category.
 */
public class PendingUploadNotOwnedException extends RuntimeException {

    public PendingUploadNotOwnedException(String message) {
        super(message);
    }
}