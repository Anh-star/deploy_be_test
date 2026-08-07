package com.cmcu.itstudy.handle;

/**
 * Thrown when a paid-create request tries to bind an {@code uploadId}
 * that has already been BOUND to another Document, or has been
 * CANCELED, EXPIRED, CLEANING, etc.
 *
 * <p>HTTP semantics: 409. The bind is a single-use operation; replay
 * is rejected.
 */
public class PendingUploadAlreadyBoundException extends RuntimeException {

    public PendingUploadAlreadyBoundException(String message) {
        super(message);
    }
}