package com.cmcu.itstudy.handle;

/**
 * Thrown when the actual content type on Supabase differs from the
 * MIME type declared at paid-upload-target time.
 *
 * <p>HTTP semantics: 409. The pending row is atomically marked
 * {@code CANCELED} and a {@code BIND_FAIL_NEW} cleanup task is
 * enqueued.
 */
public class StorageObjectMimeMismatchException extends RuntimeException {

    public StorageObjectMimeMismatchException(String message) {
        super(message);
    }
}