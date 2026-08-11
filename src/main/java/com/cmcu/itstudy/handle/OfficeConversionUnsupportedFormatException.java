package com.cmcu.itstudy.handle;

/**
 * Thrown when an Office input extension or MIME is not part of the
 * DOC / DOCX whitelist. Terminal: never retry.
 */
public class OfficeConversionUnsupportedFormatException extends OfficeConversionTerminalException {

    public OfficeConversionUnsupportedFormatException(String message) {
        super("UNSUPPORTED_FORMAT", message);
    }
}
