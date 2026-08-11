package com.cmcu.itstudy.handle;

/**
 * Thrown when the requested file extension and MIME type are not in the
 * {@link com.cmcu.itstudy.enums.AllowedDocumentFileType} whitelist.
 */
public class UnsupportedFileTypeException extends RuntimeException {
    public UnsupportedFileTypeException(String message) {
        super(message);
    }
}
