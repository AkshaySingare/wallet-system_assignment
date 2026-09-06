package com.example.wallet_system.enums;

/**
 * Outcome of a money operation.
 *
 * <p>Only {@link #SUCCESS} rows are persisted by this application: a business
 * failure (e.g. insufficient balance) rolls the whole transaction back so no
 * ledger row is written. The {@link #FAILED} value exists to model explicitly
 * recorded failures should the policy change, and to keep the schema expressive.
 */
public enum TransactionStatus {
    SUCCESS,
    FAILED
}
