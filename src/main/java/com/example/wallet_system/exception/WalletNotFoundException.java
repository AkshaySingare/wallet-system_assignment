package com.example.wallet_system.exception;

public class WalletNotFoundException extends ApiException {

    public WalletNotFoundException(String message) {
        super(ErrorCode.WALLET_NOT_FOUND, message);
    }
}
