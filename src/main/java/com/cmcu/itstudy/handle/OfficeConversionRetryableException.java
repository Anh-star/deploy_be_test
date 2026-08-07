package com.cmcu.itstudy.handle;

/**
 * Marker for conversion failures that Phase&nbsp;O3 may treat as
 * {@code RETRY} when the attempt count has not yet exhausted the
 * configured maximum.
 */
public abstract class OfficeConversionRetryableException extends OfficeConversionException {

    protected OfficeConversionRetryableException(String failureCode, String message) {
        super(failureCode, message);
    }

    protected OfficeConversionRetryableException(String failureCode, String message, Throwable cause) {
        super(failureCode, message, cause);
    }
}
