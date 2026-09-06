package com.example.wallet_system.security;

import com.example.wallet_system.dto.response.ErrorResponse;
import com.example.wallet_system.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

/**
 * Shared helper that writes an {@link ErrorResponse} for security failures that
 * occur before the request reaches {@code @RestControllerAdvice} (auth entry
 * point / access-denied handler), keeping the error shape identical everywhere.
 */
final class SecurityErrorWriter {

    private SecurityErrorWriter() {
    }

    static void write(
        ObjectMapper objectMapper,
        HttpServletResponse response,
        HttpServletRequest request,
        ErrorCode code,
        String message) throws IOException {
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = new ErrorResponse(
            Instant.now(),
            code.getStatus().value(),
            code.name(),
            message,
            request.getRequestURI(),
            null);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
