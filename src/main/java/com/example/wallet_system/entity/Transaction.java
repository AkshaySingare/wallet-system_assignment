package com.example.wallet_system.entity;

import com.example.wallet_system.enums.TransactionStatus;
import com.example.wallet_system.enums.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Immutable ledger row describing one money movement.
 *
 * <p><b>Nullable {@code from_wallet}:</b> for a {@link TransactionType#ADD}
 * top-up there is no source wallet — money enters the system — so
 * {@code from_wallet} is intentionally null and {@code to_wallet} is the user's
 * wallet. For a {@link TransactionType#TRANSFER} both sides are required.
 *
 * <p>The {@code idempotency_key} column carries a unique constraint: this is
 * the database-level guarantee that a given money operation is recorded at most
 * once, even under concurrent duplicate requests.
 */
@Entity
@Table(
    name = "transactions",
    uniqueConstraints = @UniqueConstraint(name = "uk_transactions_idempotency_key", columnNames = "idempotency_key"),
    indexes = {
        @Index(name = "idx_tx_from_wallet", columnList = "from_wallet_id"),
        @Index(name = "idx_tx_to_wallet", columnList = "to_wallet_id")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null for ADD (top-up). Required for TRANSFER. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_wallet_id", foreignKey = @jakarta.persistence.ForeignKey(name = "fk_tx_from_wallet"))
    private Wallet fromWallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_wallet_id", nullable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "fk_tx_to_wallet"))
    private Wallet toWallet;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
