package com.cmcu.itstudy.handle;

/**
 * Guard exception raised by the converter when a non-document input
 * (blank executable name, null profile / output / input path) violates
 * an internal pre-condition.
 *
 * <p>This exception is NOT a per-document terminal failure. It is
 * reclassified as RETRYABLE so a transient operator misconfiguration
 * never causes an individual document to be marked DEAD
 * permanently. The corresponding bean-level validation lives in
 * {@code OfficePreviewConfiguration} and fails fast at Spring
 * startup, so this exception is only reachable in defensive code
 * paths that handle programmer error.</p>
 */
public class OfficeConversionConfigurationException extends OfficeConversionRetryableException {

    public OfficeConversionConfigurationException(String message) {
        super("LO_CONFIG", message);
    }
}
