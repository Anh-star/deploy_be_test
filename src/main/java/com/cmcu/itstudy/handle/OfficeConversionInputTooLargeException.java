package com.cmcu.itstudy.handle;

/**
 * Thrown when an Office input exceeds the configured hard size cap.
 * Terminal: never retry the same input.
 */
public class OfficeConversionInputTooLargeException extends OfficeConversionTerminalException {

    public OfficeConversionInputTooLargeException(String message) {
        super("INPUT_TOO_LARGE", message);
    }
}
