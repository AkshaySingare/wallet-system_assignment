package com.example.wallet_system.exception;

public class DuplicateEmailException extends ApiException {

    public DuplicateEmailException(String email) {
        super(ErrorCode.DUPLICATE_EMAIL, "Email already registered: " + email);
    }
}
