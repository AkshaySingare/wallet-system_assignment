package com.example.wallet_system.dto.response;

import java.math.BigDecimal;

/** Wallet view for admins, including the owning user's identity. */
public record AdminWalletResponse(Long walletId, Long userId, String email, BigDecimal balance) {
}
