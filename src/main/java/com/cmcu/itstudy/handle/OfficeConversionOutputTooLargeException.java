package com.cmcu.itstudy.handle;

/**
 * Thrown when the LibreOffice output PDF exceeds the configured hard
 * size cap. Terminal: never retry the same input.
 */
public class OfficeConversionOutputTooLargeException extends OfficeConversionTerminalException {

    public OfficeConversionOutputTooLargeException(String message) {
        super("OUTPUT_TOO_LARGE", message);
    }
}
