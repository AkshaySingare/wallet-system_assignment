package com.example.wallet_system.mapper;

import com.example.wallet_system.dto.response.AdminWalletResponse;
import com.example.wallet_system.dto.response.TransactionResponse;
import com.example.wallet_system.dto.response.WalletResponse;
import com.example.wallet_system.entity.Transaction;
import com.example.wallet_system.entity.Wallet;
import org.springframework.stereotype.Component;

/** Maps entities to response DTOs so JPA entities are never exposed directly. */
@Component
public class WalletMapper {

    public WalletResponse toWalletResponse(Wallet wallet) {
        return new WalletResponse(wallet.getId(), wallet.getBalance());
    }

    public AdminWalletResponse toAdminWalletResponse(Wallet wallet) {
        return new AdminWalletResponse(
            wallet.getId(),
            wallet.getUser().getId(),
            wallet.getUser().getEmail(),
            wallet.getBalance());
    }

    public TransactionResponse toTransactionResponse(Transaction tx) {
        return new TransactionResponse(
            tx.getId(),
            tx.getFromWallet() != null ? tx.getFromWallet().getId() : null,
            tx.getToWallet().getId(),
            tx.getAmount(),
            tx.getType().name(),
            tx.getStatus().name(),
            tx.getIdempotencyKey(),
            tx.getCreatedAt());
    }
}
