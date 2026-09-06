package com.example.wallet_system.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Ledger row view. {@code fromWalletId} is null for ADD (top-up) transactions.
 */
public record TransactionResponse(
    Long id,
    Long fromWalletId,
    Long toWalletId,
    BigDecimal amount,
    String type,
    String status,
    String idempotencyKey,
    Instant createdAt
) {
}
