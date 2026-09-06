package com.example.wallet_system.exception;

import com.example.wallet_system.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Central error handling. Produces a consistent {@link ErrorResponse} shape and
 * never leaks stack traces to clients. Unexpected errors are logged server-side
 * and returned as a generic 500.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
        return build(ex.getErrorCode(), ex.getMessage(), request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
        MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
            .map(this::toFieldError)
            .toList();
        return build(ErrorCode.VALIDATION_ERROR, "Request validation failed", request, fieldErrors);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(
        MissingRequestHeaderException ex, HttpServletRequest request) {
        return build(ErrorCode.INVALID_REQUEST, "Missing required header: " + ex.getHeaderName(), request, null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(
        HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(ErrorCode.INVALID_REQUEST, "Malformed or missing request body", request, null);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
        BadCredentialsException ex, HttpServletRequest request) {
        return build(ErrorCode.UNAUTHORIZED, "Invalid email or password", request, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
        AccessDeniedException ex, HttpServletRequest request) {
        return build(ErrorCode.FORBIDDEN, "You do not have permission to access this resource", request, null);
    }

    /**
     * Unique-constraint or other integrity violations that reach the controller
     * layer (e.g. a duplicate that could not be resolved to a winning row).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(
        DataIntegrityViolationException ex, HttpServletRequest request) {
        return build(ErrorCode.IDEMPOTENCY_KEY_CONFLICT, "Request conflicts with an existing record", request, null);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(
        ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        return build(
            ErrorCode.CONCURRENT_MODIFICATION, "The resource was modified concurrently, please retry", request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", request, null);
    }

    private ErrorResponse.FieldError toFieldError(FieldError fieldError) {
        return new ErrorResponse.FieldError(fieldError.getField(), fieldError.getDefaultMessage());
    }

    private ResponseEntity<ErrorResponse> build(
        ErrorCode code, String message, HttpServletRequest request, List<ErrorResponse.FieldError> fieldErrors) {
        HttpStatus status = code.getStatus();
        ErrorResponse body = new ErrorResponse(
            Instant.now(),
            status.value(),
            code.name(),
            message,
            request.getRequestURI(),
            fieldErrors);
        return ResponseEntity.status(status).body(body);
    }
}
