package com.cmcu.itstudy.handle;

/**
 * Base type for every typed conversion failure emitted by
 * {@code LibreOfficeDocumentConverter}.
 *
 * <p>Subclasses are split into two families:</p>
 * <ul>
 *   <li>{@link OfficeConversionRetryableException} — Phase&nbsp;O3 maps
 *       these to a {@code RETRY} transition when attempt_count is below
 *       the configured maximum, otherwise to {@code DEAD}.</li>
 *   <li>{@link OfficeConversionTerminalException} — Phase&nbsp;O3 maps
 *       these to {@code DEAD} regardless of attempt_count.</li>
 * </ul>
 *
 * <p>Every subtype carries a safe short failure code that callers may
 * persist in {@code last_error}; the full message is preserved for
 * local logs only.</p>
 */
public abstract class OfficeConversionException extends RuntimeException {

    private final String failureCode;

    protected OfficeConversionException(String failureCode, String message) {
        super(message);
        this.failureCode = failureCode;
    }

    protected OfficeConversionException(String failureCode, String message, Throwable cause) {
        super(message, cause);
        this.failureCode = failureCode;
    }

    public String getFailureCode() {
        return failureCode;
    }
}
