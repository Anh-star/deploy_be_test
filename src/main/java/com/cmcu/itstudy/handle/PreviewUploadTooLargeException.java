package com.cmcu.itstudy.handle;

/**
 * Thrown when the worker tries to upload a PDF preview whose byte
 * payload exceeds the configured hard cap.
 *
 * <p>The Phase&nbsp;O1 PDF validation pipeline already enforces a
 * {@code 25&nbsp;MiB} output cap, so this exception is only a
 * defence-in-depth signal: it indicates that the worker attempted to
 * upload a payload that should have been rejected earlier in the
 * pipeline. The worker maps this exception to a terminal
 * {@code DEAD} transition because silently truncating a generated PDF
 * would produce a corrupt preview.</p>
 *
 * <p>This exception is intentionally narrow and is not handled by
 * {@link GlobalExceptionHandler} &mdash; the worker dispatcher
 * translates it into a guarded repository transition.</p>
 */
public class PreviewUploadTooLargeException extends RuntimeException {

    public PreviewUploadTooLargeException(String message) {
        super(message);
    }
}
