package com.example.wallet_system.dto.response;

/** JWT bearer token returned on successful login. */
public record AuthResponse(String token, String tokenType) {

    public static AuthResponse bearer(String token) {
        return new AuthResponse(token, "Bearer");
    }
}
