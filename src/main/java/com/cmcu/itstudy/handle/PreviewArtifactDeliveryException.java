package com.cmcu.itstudy.handle;

/**
 * Phase&nbsp;O4B: terminal delivery error raised by
 * {@code DocumentPreviewArtifactDeliveryService} when the artifact row,
 * the storage bytes, or the PDF contract is irretrievably invalid.
 *
 * <p>This exception signals a SAFE TERMINAL state to the controller:
 * the controller translates it into a non-leaking terminal JSON
 * descriptor that stops frontend polling. There is no retry path:
 * the artifact is malformed, the storage bucket/path is blank, or
 * the bytes are not a PDF &mdash; none of these can be cured by a
 * re-poll.</p>
 *
 * <p>The exception carries no bucket, path, stack trace, or
 * internal Supabase payload to the client.</p>
 */
public class PreviewArtifactDeliveryException extends RuntimeException {
    public PreviewArtifactDeliveryException(String message) {
        super(message);
    }
    public PreviewArtifactDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}