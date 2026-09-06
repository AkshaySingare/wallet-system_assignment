package com.example.wallet_system.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registration payload. Password rules are intentionally light but non-trivial:
 * 8..72 chars (72 is the BCrypt input limit) so the sample password
 * {@code Password123} is accepted.
 */
public record RegisterRequest(
    @NotBlank(message = "email must not be blank")
    @Email(message = "email must be a valid address")
    String email,

    @NotBlank(message = "password must not be blank")
    @Size(min = 8, max = 72, message = "password must be between 8 and 72 characters")
    String password
) {
}
