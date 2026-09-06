package com.example.wallet_system.enums;

/**
 * Kind of money movement recorded in the ledger.
 *
 * <ul>
 *   <li>{@code ADD} — money added to a wallet; has no source wallet (top-up).</li>
 *   <li>{@code TRANSFER} — money moved between two wallets; both are present.</li>
 * </ul>
 */
public enum TransactionType {
    ADD,
    TRANSFER
}
