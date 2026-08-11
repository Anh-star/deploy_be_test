package com.cmcu.itstudy.handle;

/**
 * Thrown when the operator has not configured the private document
 * bucket name in {@code SupabaseProperties}.
 */
public class PrivateBucketNotConfiguredException extends RuntimeException {
    public PrivateBucketNotConfiguredException(String message) {
        super(message);
    }
}
