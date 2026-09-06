package com.example.wallet_system.repository;

import com.example.wallet_system.entity.Wallet;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByUserId(Long userId);

    /**
     * Scalar lookup of a wallet's id by owning user id. Used to decide a
     * deterministic pessimistic-lock ordering <em>without</em> loading the
     * wallet entity into the persistence context first — loading it unlocked
     * would pin a stale {@code @Version} and cause a spurious optimistic-lock
     * failure at commit even though a pessimistic lock is later held.
     */
    @Query("select w.id from Wallet w where w.user.id = :userId")
    Optional<Long> findWalletIdByUserId(@Param("userId") Long userId);

    /**
     * Fetch a wallet by owning user id while acquiring a {@code PESSIMISTIC_WRITE}
     * row lock. Used inside the transactional core of money operations so that
     * concurrent modifications to the same wallet are serialized by the database.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.user.id = :userId")
    Optional<Wallet> findByUserIdForUpdate(@Param("userId") Long userId);

    /**
     * Fetch a wallet by primary key with a {@code PESSIMISTIC_WRITE} row lock.
     * Transfers lock both wallets through this method in a deterministic order
     * (ascending id) to avoid deadlocks.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.id = :id")
    Optional<Wallet> findByIdForUpdate(@Param("id") Long id);
}
