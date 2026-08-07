package com.cmcu.itstudy.handle;

/**
 * Thrown when a conversion was interrupted while waiting for the
 * process tree to terminate. This is a retryable condition; the
 * interrupt flag has already been restored by the runner.
 */
public class OfficeConversionInterruptedException extends OfficeConversionRetryableException {

    public OfficeConversionInterruptedException(String message, Throwable cause) {
        super("INTERRUPTED", message, cause);
    }
}
