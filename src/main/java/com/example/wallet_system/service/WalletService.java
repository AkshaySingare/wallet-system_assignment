package com.example.wallet_system.service;

import com.example.wallet_system.dto.request.AddMoneyRequest;
import com.example.wallet_system.dto.request.TransferRequest;
import com.example.wallet_system.dto.response.PageResponse;
import com.example.wallet_system.dto.response.TransactionResponse;
import com.example.wallet_system.dto.response.WalletResponse;
import org.springframework.data.domain.Pageable;

public interface WalletService {

    WalletResponse getWalletForUser(Long userId);

    TransactionResponse addMoney(Long userId, AddMoneyRequest request, String idempotencyKey);

    TransactionResponse transfer(Long senderUserId, TransferRequest request, String idempotencyKey);

    PageResponse<TransactionResponse> getTransactionsForUser(Long userId, Pageable pageable);
}
