package com.cmcu.itstudy.handle;

/**
 * Marker for conversion failures that must always map to
 * {@code DEAD} regardless of how many attempts remain.
 */
public abstract class OfficeConversionTerminalException extends OfficeConversionException {

    protected OfficeConversionTerminalException(String failureCode, String message) {
        super(failureCode, message);
    }

    protected OfficeConversionTerminalException(String failureCode, String message, Throwable cause) {
        super(failureCode, message, cause);
    }
}
