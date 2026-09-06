package com.example.wallet_system.dto.response;

/** Confirmation returned after registration. Never contains the password. */
public record RegisterResponse(Long userId, String email, String role, Long walletId) {
}
