package com.example.wallet_system.dto.response;

import java.math.BigDecimal;

public record WalletResponse(Long walletId, BigDecimal balance) {
}
