package com.example.wallet_system.service.impl;

import com.example.wallet_system.dto.response.AdminWalletResponse;
import com.example.wallet_system.dto.response.PageResponse;
import com.example.wallet_system.dto.response.TransactionResponse;
import com.example.wallet_system.mapper.WalletMapper;
import com.example.wallet_system.repository.TransactionRepository;
import com.example.wallet_system.repository.WalletRepository;
import com.example.wallet_system.service.AdminService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminServiceImpl implements AdminService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final WalletMapper walletMapper;

    public AdminServiceImpl(
        WalletRepository walletRepository,
        TransactionRepository transactionRepository,
        WalletMapper walletMapper) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.walletMapper = walletMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminWalletResponse> getAllWallets(Pageable pageable) {
        return PageResponse.from(
            walletRepository.findAll(pageable).map(walletMapper::toAdminWalletResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> getAllTransactions(Pageable pageable) {
        return PageResponse.from(
            transactionRepository.findAll(pageable).map(walletMapper::toTransactionResponse));
    }
}
