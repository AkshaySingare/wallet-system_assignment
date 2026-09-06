package com.example.wallet_system.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.wallet_system.dto.response.ErrorResponse;
import com.example.wallet_system.exception.ApiException;
import com.example.wallet_system.exception.ErrorCode;
import com.example.wallet_system.exception.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

/** Unit tests for the branches of the global handler not hit via the API. */
class ExceptionHandlingTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private HttpServletRequest request(String uri) {
        HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
        Mockito.when(req.getRequestURI()).thenReturn(uri);
        Mockito.when(req.getMethod()).thenReturn("POST");
        return req;
    }

    @Test
    void apiExceptionMapsToConfiguredStatus() {
        ResponseEntity<ErrorResponse> response =
            handler.handleApiException(new ApiException(ErrorCode.WALLET_NOT_FOUND, "nope"), request("/wallet"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().error()).isEqualTo("WALLET_NOT_FOUND");
        assertThat(response.getBody().path()).isEqualTo("/wallet");
    }

    @Test
    void optimisticLockMapsToConflict() {
        ResponseEntity<ErrorResponse> response = handler.handleOptimisticLock(
            new ObjectOptimisticLockingFailureException(Object.class, 1L), request("/wallet/transfer"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().error()).isEqualTo("CONCURRENT_MODIFICATION");
    }

    @Test
    void dataIntegrityMapsToConflict() {
        ResponseEntity<ErrorResponse> response =
            handler.handleDataIntegrity(new DataIntegrityViolationException("dup"), request("/wallet/add"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().error()).isEqualTo("IDEMPOTENCY_KEY_CONFLICT");
    }

    @Test
    void badCredentialsMapsToUnauthorized() {
        ResponseEntity<ErrorResponse> response =
            handler.handleBadCredentials(new BadCredentialsException("bad"), request("/auth/login"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void accessDeniedMapsToForbidden() {
        ResponseEntity<ErrorResponse> response =
            handler.handleAccessDenied(new AccessDeniedException("no"), request("/admin/wallets"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void unexpectedMapsToInternalError() {
        ResponseEntity<ErrorResponse> response =
            handler.handleUnexpected(new RuntimeException("boom"), request("/wallet"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().error()).isEqualTo("INTERNAL_ERROR");
        // Never leak the underlying message.
        assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred");
    }
}
