package com.example.wallet_system.controller;

import com.example.wallet_system.dto.request.AddMoneyRequest;
import com.example.wallet_system.dto.request.TransferRequest;
import com.example.wallet_system.dto.response.PageResponse;
import com.example.wallet_system.dto.response.TransactionResponse;
import com.example.wallet_system.dto.response.WalletResponse;
import com.example.wallet_system.exception.MissingIdempotencyKeyException;
import com.example.wallet_system.security.SecurityUtil;
import com.example.wallet_system.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * User-facing wallet APIs. The wallet is always derived from the authenticated
 * user id in the security context — no wallet id is ever accepted from the
 * request, so a user cannot reach another user's wallet.
 */
@RestController
@RequestMapping("/wallet")
public class WalletController {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping
    public ResponseEntity<WalletResponse> getWallet() {
        return ResponseEntity.ok(walletService.getWalletForUser(SecurityUtil.currentUserId()));
    }

    @PostMapping("/add")
    public ResponseEntity<TransactionResponse> addMoney(
        @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
        @Valid @RequestBody AddMoneyRequest request) {
        String key = requireIdempotencyKey(idempotencyKey);
        return ResponseEntity.ok(walletService.addMoney(SecurityUtil.currentUserId(), request, key));
    }

    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> transfer(
        @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
        @Valid @RequestBody TransferRequest request) {
        String key = requireIdempotencyKey(idempotencyKey);
        return ResponseEntity.ok(walletService.transfer(SecurityUtil.currentUserId(), request, key));
    }

    @GetMapping("/transactions")
    public ResponseEntity<PageResponse<TransactionResponse>> getTransactions(Pageable pageable) {
        return ResponseEntity.ok(walletService.getTransactionsForUser(SecurityUtil.currentUserId(), pageable));
    }

    private String requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new MissingIdempotencyKeyException();
        }
        return idempotencyKey.trim();
    }
}
