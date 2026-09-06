package com.example.wallet_system.exception;

/**
 * Base type for all domain exceptions. Carries a stable {@link ErrorCode} that
 * the global handler maps to an HTTP status and error payload.
 */
public class ApiException extends RuntimeException {

    private final transient ErrorCode errorCode;

    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
