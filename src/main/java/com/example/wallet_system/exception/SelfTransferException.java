package com.example.wallet_system.exception;

public class SelfTransferException extends ApiException {

    public SelfTransferException() {
        super(ErrorCode.SELF_TRANSFER, "Cannot transfer to your own wallet");
    }
}
