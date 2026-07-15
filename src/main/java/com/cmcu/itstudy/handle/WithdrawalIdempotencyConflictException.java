package com.cmcu.itstudy.handle;

public class WithdrawalIdempotencyConflictException extends RuntimeException {

    public WithdrawalIdempotencyConflictException(String message) {
        super(message);
    }
}
