package com.cmcu.itstudy.handle;

/**
 * Thrown when a LibreOffice conversion exceeds the configured wall
 * clock budget. Retryable; the runner has already terminated the
 * process tree by the time this exception propagates.
 */
public class OfficeConversionTimeoutException extends OfficeConversionRetryableException {

    public OfficeConversionTimeoutException(String message) {
        super("LO_TIMEOUT", message);
    }
}
