package com.example.wallet_system.service;

import com.example.wallet_system.dto.response.AdminWalletResponse;
import com.example.wallet_system.dto.response.PageResponse;
import com.example.wallet_system.dto.response.TransactionResponse;
import org.springframework.data.domain.Pageable;

public interface AdminService {

    PageResponse<AdminWalletResponse> getAllWallets(Pageable pageable);

    PageResponse<TransactionResponse> getAllTransactions(Pageable pageable);
}
