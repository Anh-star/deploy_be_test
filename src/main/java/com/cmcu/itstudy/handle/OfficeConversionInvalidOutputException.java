package com.cmcu.itstudy.handle;

/**
 * Thrown when a LibreOffice run exits with code 0 but the output
 * directory is empty, contains more than one candidate PDF, or the
 * PDF cannot be loaded by PDFBox. Terminal: never retry.
 */
public class OfficeConversionInvalidOutputException extends OfficeConversionTerminalException {

    public OfficeConversionInvalidOutputException(String failureCode, String message) {
        super(failureCode, message);
    }

    public OfficeConversionInvalidOutputException(String failureCode, String message, Throwable cause) {
        super(failureCode, message, cause);
    }
}
