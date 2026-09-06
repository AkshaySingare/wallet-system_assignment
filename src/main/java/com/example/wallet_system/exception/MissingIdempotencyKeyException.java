package com.example.wallet_system.exception;

public class MissingIdempotencyKeyException extends ApiException {

    public MissingIdempotencyKeyException() {
        super(ErrorCode.MISSING_IDEMPOTENCY_KEY, "Idempotency-Key header is required");
    }
}
