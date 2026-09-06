package com.example.wallet_system.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money helpers. All monetary values are normalized to scale 2 using
 * {@link RoundingMode#HALF_EVEN} ("banker's rounding"), which avoids the upward
 * bias of HALF_UP across many operations. Inputs are already constrained to at
 * most 2 fraction digits by bean validation, so this normalization is defensive.
 */
public final class MoneyUtil {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;
    public static final BigDecimal ZERO = normalize(BigDecimal.ZERO);

    private MoneyUtil() {
    }

    public static BigDecimal normalize(BigDecimal value) {
        return value.setScale(SCALE, ROUNDING);
    }
}
