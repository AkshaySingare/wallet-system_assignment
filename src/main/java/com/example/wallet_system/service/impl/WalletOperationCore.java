package com.example.wallet_system.service.impl;

import com.example.wallet_system.entity.Transaction;
import com.example.wallet_system.entity.User;
import com.example.wallet_system.entity.Wallet;
import com.example.wallet_system.enums.TransactionStatus;
import com.example.wallet_system.enums.TransactionType;
import com.example.wallet_system.exception.InsufficientBalanceException;
import com.example.wallet_system.exception.SelfTransferException;
import com.example.wallet_system.exception.UserNotFoundException;
import com.example.wallet_system.exception.WalletNotFoundException;
import com.example.wallet_system.repository.TransactionRepository;
import com.example.wallet_system.repository.UserRepository;
import com.example.wallet_system.repository.WalletRepository;
import com.example.wallet_system.util.MoneyUtil;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional core of the money operations. Kept as a separate bean (rather
 * than {@code private} methods on the orchestrator) so that Spring's
 * transactional proxy actually applies — a self-invocation would bypass it.
 *
 * <p>Every public method here runs in its own {@code REQUIRES_NEW} transaction.
 * That is what makes the idempotency guarantee correct: when a concurrent
 * duplicate loses the race on the unique {@code idempotency_key} constraint, the
 * failure rolls back <em>this</em> transaction only (including its balance
 * mutation), leaving the winner's committed result intact for the orchestrator
 * to return.
 *
 * <p><b>Locking:</b> wallets are fetched with {@code PESSIMISTIC_WRITE} row
 * locks. Transfers lock the two wallets in a deterministic order (ascending
 * wallet id) so two transfers touching the same pair can never deadlock by
 * grabbing the locks in opposite orders.
 */
@Component
public class WalletOperationCore {

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    public WalletOperationCore(
        WalletRepository walletRepository,
        UserRepository userRepository,
        TransactionRepository transactionRepository) {
        this.walletRepository = walletRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Transaction addMoney(Long userId, BigDecimal rawAmount, String idempotencyKey) {
        BigDecimal amount = MoneyUtil.normalize(rawAmount);

        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
            .orElseThrow(() -> new WalletNotFoundException("Wallet not found for current user"));

        wallet.setBalance(wallet.getBalance().add(amount));

        Transaction tx = Transaction.builder()
            .fromWallet(null) // top-up: money enters the system, no source wallet
            .toWallet(wallet)
            .amount(amount)
            .type(TransactionType.ADD)
            .status(TransactionStatus.SUCCESS)
            .idempotencyKey(idempotencyKey)
            .createdAt(Instant.now())
            .build();

        // Flush now so a duplicate idempotency key trips the unique constraint
        // inside this transaction (and rolls back the balance change) rather
        // than at commit time.
        return transactionRepository.saveAndFlush(tx);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Transaction transfer(Long senderUserId, Long toUserId, BigDecimal rawAmount, String idempotencyKey) {
        if (senderUserId.equals(toUserId)) {
            throw new SelfTransferException();
        }
        BigDecimal amount = MoneyUtil.normalize(rawAmount);

        // Resolve wallet ids via scalar queries (no entity load) to decide a
        // deterministic lock order, then acquire the write locks in ascending id
        // order. Locking before any unlocked load avoids pinning a stale version.
        Long senderWalletId = walletRepository.findWalletIdByUserId(senderUserId)
            .orElseThrow(() -> new WalletNotFoundException("Wallet not found for current user"));
        User receiver = userRepository.findById(toUserId)
            .orElseThrow(() -> new UserNotFoundException("Recipient user not found: " + toUserId));
        Long receiverWalletId = walletRepository.findWalletIdByUserId(receiver.getId())
            .orElseThrow(() -> new WalletNotFoundException("Recipient wallet not found"));

        Wallet sender;
        Wallet receiverWallet;
        if (senderWalletId < receiverWalletId) {
            sender = lockById(senderWalletId);
            receiverWallet = lockById(receiverWalletId);
        } else {
            receiverWallet = lockById(receiverWalletId);
            sender = lockById(senderWalletId);
        }

        if (sender.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException();
        }

        sender.setBalance(sender.getBalance().subtract(amount));
        receiverWallet.setBalance(receiverWallet.getBalance().add(amount));

        Transaction tx = Transaction.builder()
            .fromWallet(sender)
            .toWallet(receiverWallet)
            .amount(amount)
            .type(TransactionType.TRANSFER)
            .status(TransactionStatus.SUCCESS)
            .idempotencyKey(idempotencyKey)
            .createdAt(Instant.now())
            .build();

        return transactionRepository.saveAndFlush(tx);
    }

    private Wallet lockById(Long walletId) {
        return walletRepository.findByIdForUpdate(walletId)
            .orElseThrow(() -> new WalletNotFoundException("Wallet not found: " + walletId));
    }
}
