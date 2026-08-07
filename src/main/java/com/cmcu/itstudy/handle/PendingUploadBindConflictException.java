package com.cmcu.itstudy.handle;

/**
 * Thrown when the binder transaction cannot flip a {@code PENDING}
 * upload to {@code BOUND} because it has already been moved to
 * {@code BOUND}, {@code CANCELED}, {@code EXPIRED}, or
 * {@code CLEANING} by a concurrent / replayed request, or because the
 * bind UPDATE affected 0 rows.
 *
 * <p>HTTP semantics: 409. The caller is expected to start a new
 * signed-upload-target flow and retry. The binder has not committed
 * any Document / DocumentFile rows in this state.
 */
public class PendingUploadBindConflictException extends RuntimeException {

    public PendingUploadBindConflictException(String message) {
        super(message);
    }

    public PendingUploadBindConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
