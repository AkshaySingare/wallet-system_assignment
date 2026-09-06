package com.example.wallet_system.repository;

import com.example.wallet_system.entity.Transaction;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    /**
     * All transactions touching the given wallet (as source or destination),
     * newest first. Used for a user's own paginated history.
     */
    @Query("select t from Transaction t "
        + "where t.fromWallet.id = :walletId or t.toWallet.id = :walletId "
        + "order by t.createdAt desc, t.id desc")
    Page<Transaction> findByWalletId(@Param("walletId") Long walletId, Pageable pageable);
}
