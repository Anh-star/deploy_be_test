package com.cmcu.itstudy.handle;

/**
 * Thrown when the actual object size on Supabase differs from the
 * size declared at paid-upload-target time.
 *
 * <p>HTTP semantics: 409. The pending row is atomically marked
 * {@code CANCELED} and a {@code BIND_FAIL_NEW} cleanup task is
 * enqueued so the remote object is eventually deleted by the
 * background worker (NOT by this transaction).
 */
public class StorageObjectSizeMismatchException extends RuntimeException {

    public StorageObjectSizeMismatchException(String message) {
        super(message);
    }
}