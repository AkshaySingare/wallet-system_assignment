package com.example.wallet_system.exception;

public class InsufficientBalanceException extends ApiException {

    public InsufficientBalanceException() {
        super(ErrorCode.INSUFFICIENT_BALANCE, "Insufficient wallet balance");
    }
}
