package com.cmcu.itstudy.handle;

/**
 * Thrown when the Supabase signed-upload-target API call fails for any
 * reason. The cause is logged server-side but never returned to the
 * caller; this exception's message is safe.
 *
 * <p>This exception optionally carries an internal category (server-side
 * correlation only); the public message is always a single safe string.
 */
public class SignedUploadTargetFailedException extends RuntimeException {
    private final String internalCategory;

    public SignedUploadTargetFailedException(String message) {
        this(message, null, null);
    }

    public SignedUploadTargetFailedException(String message, String internalCategory) {
        this(message, internalCategory, null);
    }

    public SignedUploadTargetFailedException(String message, Throwable cause) {
        this(message, null, cause);
    }

    private SignedUploadTargetFailedException(
            String message, String internalCategory, Throwable cause) {
        super(message, cause);
        this.internalCategory = internalCategory;
    }

    /**
     * Server-side-only category describing the failure cause (for example
     * {@code "missing-url"} or {@code "missing-service-role-key"}). The
     * value is never surfaced to the caller.
     */
    public String getInternalCategory() {
        return internalCategory;
    }
}