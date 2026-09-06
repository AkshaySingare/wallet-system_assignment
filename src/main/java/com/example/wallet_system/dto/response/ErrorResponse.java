package com.example.wallet_system.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * Consistent error payload for all failures. {@code fieldErrors} is populated
 * only for validation failures.
 */
public record ErrorResponse(
    Instant timestamp,
    int status,
    String error,
    String message,
    String path,
    List<FieldError> fieldErrors
) {

    public record FieldError(String field, String message) {
    }
}
