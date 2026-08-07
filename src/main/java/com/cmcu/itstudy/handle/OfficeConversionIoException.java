package com.cmcu.itstudy.handle;

/**
 * Thrown when the conversion hits a retryable I/O error on the local
 * filesystem (for example a transient permission issue, a temporary
 * disk full condition, an interrupted temp directory creation, a
 * failed Office input write, or a failed output read).
 *
 * <p>This is a RETRYABLE condition: a transient local I/O failure
 * must not push the document into a terminal DEAD state. Phase&nbsp;O3
 * may map it to {@code RETRY} when {@code attempt_count < max_attempts},
 * otherwise to {@code DEAD}.</p>
 *
 * <p>The original {@link Throwable} cause is preserved for diagnostics
 * but is NEVER persisted to logs; only the failure code and the
 * caller-supplied message are exposed.</p>
 */
public class OfficeConversionIoException extends OfficeConversionRetryableException {

    public OfficeConversionIoException(String message, Throwable cause) {
        super("IO_ERROR", message, cause);
    }
}
