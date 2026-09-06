package com.example.wallet_system.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record TransferRequest(
    @NotNull(message = "toUserId must not be null")
    Long toUserId,

    @NotNull(message = "amount must not be null")
    @Positive(message = "amount must be positive")
    @Digits(integer = 17, fraction = 2, message = "amount must have at most 2 decimal places")
    BigDecimal amount
) {
}
