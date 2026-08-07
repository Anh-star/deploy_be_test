package com.cmcu.itstudy.handle;

/**
 * Retryable wrapper for a generic LibreOffice process execution
 * outcome that has NOT been independently proven to be a deterministic
 * document-level failure.
 *
 * <p>This includes:</p>
 * <ul>
 *   <li>process exit code not observable (null after forced
 *       termination);</li>
 *   <li>non-zero exit code without an independently confirmed terminal
 *       input condition (corrupt container, password-protected input,
 *       unsupported feature);</li>
 *   <li>forced-termination timeout observed at the runner boundary.</li>
 * </ul>
 *
 * <p>This is a RETRYABLE condition: a transient LibreOffice failure
 * must not push the document into a terminal DEAD state. Phase&nbsp;O3
 * may map it to {@code RETRY} when
 * {@code attempt_count < max_attempts}, otherwise to {@code DEAD}.</p>
 *
 * <p>The runner NEVER parses the LibreOffice stderr to decide whether
 * a document is permanently invalid. Such classification is the
 * responsibility of the deterministic validation layer (which has
 * access to the produced PDF bytes), not the runner.</p>
 */
public class OfficeConversionProcessException extends OfficeConversionRetryableException {

    public OfficeConversionProcessException(String failureCode, String message) {
        super(failureCode, message);
    }

    public OfficeConversionProcessException(String failureCode, String message, Throwable cause) {
        super(failureCode, message, cause);
    }
}
