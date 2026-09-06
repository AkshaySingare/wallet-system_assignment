package com.example.wallet_system.exception;

import org.springframework.http.HttpStatus;

/**
 * Stable machine-readable error codes surfaced in the {@code error} field of
 * API error responses, each mapped to an HTTP status.
 */
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST),
    INVALID_AMOUNT(HttpStatus.BAD_REQUEST),
    MISSING_IDEMPOTENCY_KEY(HttpStatus.BAD_REQUEST),
    SELF_TRANSFER(HttpStatus.BAD_REQUEST),
    INSUFFICIENT_BALANCE(HttpStatus.UNPROCESSABLE_ENTITY),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT),
    IDEMPOTENCY_KEY_CONFLICT(HttpStatus.CONFLICT),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND),
    WALLET_NOT_FOUND(HttpStatus.NOT_FOUND),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
