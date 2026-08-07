package com.cmcu.itstudy.handle;

/**
 * Thrown when the original file name cannot be safely used (empty,
 * missing extension, contains path traversal, or contains a dangerous
 * double-extension).
 */
public class InvalidFileNameException extends RuntimeException {
    public InvalidFileNameException(String message) {
        super(message);
    }
}
