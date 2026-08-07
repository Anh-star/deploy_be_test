package com.cmcu.itstudy.handle;

/**
 * Thrown when the request size is invalid (null, zero, or negative).
 */
public class InvalidFileSizeException extends RuntimeException {
    public InvalidFileSizeException(String message) {
        super(message);
    }
}
