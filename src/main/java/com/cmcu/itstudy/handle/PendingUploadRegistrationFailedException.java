package com.cmcu.itstudy.handle;

/**
 * Thrown when DB persistence of the {@link com.cmcu.itstudy.entity.PendingStorageUpload}
 * row fails after a successful Supabase target creation.
 */
public class PendingUploadRegistrationFailedException extends RuntimeException {
    public PendingUploadRegistrationFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
