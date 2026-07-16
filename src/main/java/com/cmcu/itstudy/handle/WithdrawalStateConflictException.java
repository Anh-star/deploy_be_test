package com.cmcu.itstudy.handle;

public class WithdrawalStateConflictException extends RuntimeException {

    public WithdrawalStateConflictException(String message) {
        super(message);
    }
}