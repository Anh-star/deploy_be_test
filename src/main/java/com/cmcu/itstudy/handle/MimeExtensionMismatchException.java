package com.cmcu.itstudy.handle;

/**
 * Thrown when the extension and MIME type do not agree on the same
 * canonical file type.
 */
public class MimeExtensionMismatchException extends RuntimeException {
    public MimeExtensionMismatchException(String message) {
        super(message);
    }
}
