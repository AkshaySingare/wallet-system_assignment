package com.example.wallet_system.service.impl;

import com.example.wallet_system.dto.request.AddMoneyRequest;
import com.example.wallet_system.dto.request.TransferRequest;
import com.example.wallet_system.dto.response.PageResponse;
import com.example.wallet_system.dto.response.TransactionResponse;
import com.example.wallet_system.dto.response.WalletResponse;
import com.example.wallet_system.entity.Transaction;
import com.example.wallet_system.entity.Wallet;
import com.example.wallet_system.exception.ApiException;
import com.example.wallet_system.exception.ErrorCode;
import com.example.wallet_system.exception.WalletNotFoundException;
import com.example.wallet_system.mapper.WalletMapper;
import com.example.wallet_system.repository.TransactionRepository;
import com.example.wallet_system.repository.WalletRepository;
import com.example.wallet_system.service.WalletService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates wallet reads and money operations.
 *
 * <p><b>Idempotency strategy.</b> Each money operation carries a client-supplied
 * {@code Idempotency-Key}, unique at the database level on the transactions
 * table. The flow is:
 * <ol>
 *   <li>Fast path: if a transaction already exists for the key, return it
 *       without touching balances.</li>
 *   <li>Otherwise delegate to {@link WalletOperationCore}, which mutates
 *       balances and inserts the ledger row in one {@code REQUIRES_NEW}
 *       transaction.</li>
 *   <li>Race path: if two requests with the same key run concurrently, both may
 *       pass step 1. One insert wins; the other violates the unique constraint
 *       and its whole transaction (including the balance change) rolls back. We
 *       catch that here and return the winner's committed row.</li>
 * </ol>
 * This is why the money mutation and the key insert live in the same
 * transaction — the constraint can never fire "after" a committed balance
 * change.
 */
@Service
public class WalletServiceImpl implements WalletService {

    private final WalletOperationCore core;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final WalletMapper walletMapper;

    public WalletServiceImpl(
        WalletOperationCore core,
        WalletRepository walletRepository,
        TransactionRepository transactionRepository,
        WalletMapper walletMapper) {
        this.core = core;
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.walletMapper = walletMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public WalletResponse getWalletForUser(Long userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
            .orElseThrow(() -> new WalletNotFoundException("Wallet not found for current user"));
        return walletMapper.toWalletResponse(wallet);
    }

    @Override
    public TransactionResponse addMoney(Long userId, AddMoneyRequest request, String idempotencyKey) {
        Transaction existing = findExisting(idempotencyKey);
        if (existing != null) {
            return walletMapper.toTransactionResponse(existing);
        }
        try {
            Transaction tx = core.addMoney(userId, request.amount(), idempotencyKey);
            return walletMapper.toTransactionResponse(tx);
        } catch (DataIntegrityViolationException ex) {
            return resolveDuplicate(idempotencyKey);
        }
    }

    @Override
    public TransactionResponse transfer(Long senderUserId, TransferRequest request, String idempotencyKey) {
        Transaction existing = findExisting(idempotencyKey);
        if (existing != null) {
            return walletMapper.toTransactionResponse(existing);
        }
        try {
            Transaction tx = core.transfer(senderUserId, request.toUserId(), request.amount(), idempotencyKey);
            return walletMapper.toTransactionResponse(tx);
        } catch (DataIntegrityViolationException ex) {
            return resolveDuplicate(idempotencyKey);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new ApiException(
                ErrorCode.CONCURRENT_MODIFICATION,
                "The wallet was modified concurrently, please retry");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> getTransactionsForUser(Long userId, Pageable pageable) {
        Wallet wallet = walletRepository.findByUserId(userId)
            .orElseThrow(() -> new WalletNotFoundException("Wallet not found for current user"));
        return PageResponse.from(
            transactionRepository.findByWalletId(wallet.getId(), pageable)
                .map(walletMapper::toTransactionResponse));
    }

    private Transaction findExisting(String idempotencyKey) {
        return transactionRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
    }

    /**
     * A concurrent duplicate lost the unique-constraint race. The winner's row
     * is committed; return it. If it is somehow not yet visible, surface a clean
     * conflict rather than double-processing.
     */
    private TransactionResponse resolveDuplicate(String idempotencyKey) {
        Transaction winner = findExisting(idempotencyKey);
        if (winner != null) {
            return walletMapper.toTransactionResponse(winner);
        }
        throw new ApiException(
            ErrorCode.IDEMPOTENCY_KEY_CONFLICT,
            "Duplicate request is being processed, please retry");
    }
}
