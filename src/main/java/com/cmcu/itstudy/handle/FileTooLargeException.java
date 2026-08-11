package com.cmcu.itstudy.handle;

/**
 * Thrown when the request size exceeds the configured hard cap.
 */
public class FileTooLargeException extends RuntimeException {
    private final long actual;
    private final long max;

    public FileTooLargeException(long actual, long max) {
        super("File size " + actual + " exceeds maximum " + max);
        this.actual = actual;
        this.max = max;
    }

    public long getActual() {
        return actual;
    }

    public long getMax() {
        return max;
    }
}
