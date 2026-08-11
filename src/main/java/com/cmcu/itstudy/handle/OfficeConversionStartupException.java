package com.cmcu.itstudy.handle;

/**
 * Thrown when the LibreOffice process could not be started
 * (executable missing, IO failure during {@code ProcessBuilder.start},
 * temporary resource exhaustion).
 *
 * <p>This is a RETRYABLE condition: a transient filesystem / PATH /
 * executable availability problem must not push the document into a
 * terminal DEAD state. Phase&nbsp;O3 may map it to {@code RETRY} when
 * {@code attempt_count < max_attempts}, otherwise to {@code DEAD}.</p>
 */
public class OfficeConversionStartupException extends OfficeConversionRetryableException {

    public OfficeConversionStartupException(String message) {
        super("LO_STARTUP", message);
    }

    public OfficeConversionStartupException(String message, Throwable cause) {
        super("LO_STARTUP", message, cause);
    }
}
