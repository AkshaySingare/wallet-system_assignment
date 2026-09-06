package com.example.wallet_system.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A user's wallet. Exactly one per user, enforced at the database level by a
 * unique constraint on {@code user_id}.
 *
 * <p>Concurrency safety is provided in two complementary ways:
 * <ul>
 *   <li>A {@link Version} column for optimistic locking (required by the
 *       assignment).</li>
 *   <li>Row-level {@code PESSIMISTIC_WRITE} locks acquired via dedicated
 *       repository methods during money operations. This is the primary
 *       strategy used for transfers/top-ups.</li>
 * </ul>
 */
@Entity
@Table(name = "wallets", uniqueConstraints = @UniqueConstraint(name = "uk_wallets_user", columnNames = "user_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Owning user. One wallet per user; the {@code user_id} column carries a
     * unique constraint so the invariant is guaranteed by the database.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true, foreignKey = @ForeignKey(name = "fk_wallets_user"))
    private User user;

    /** Monetary balance. Scale 2. Never allowed to go negative. */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    /** Optimistic-lock version. */
    @Version
    @Column(nullable = false)
    private Long version;
}
