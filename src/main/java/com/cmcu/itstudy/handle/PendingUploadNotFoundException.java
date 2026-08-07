package com.cmcu.itstudy.handle;

/**
 * Thrown when a paid-create request references an {@code uploadId}
 * that does not exist in {@code dbo.tbl_pending_storage_uploads}.
 *
 * <p>HTTP semantics: 400 (no such pending upload). The cause category
 * is intentionally generic in the message so the caller cannot probe
 * the existence of arbitrary uploadIds.
 */
public class PendingUploadNotFoundException extends RuntimeException {

    public PendingUploadNotFoundException(String message) {
        super(message);
    }
}
