package com.example.wallet_system.controller;

import com.example.wallet_system.dto.response.AdminWalletResponse;
import com.example.wallet_system.dto.response.PageResponse;
import com.example.wallet_system.dto.response.TransactionResponse;
import com.example.wallet_system.service.AdminService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only APIs. Guarded twice: by the URL rule ({@code /admin/**} requires
 * ADMIN in {@code SecurityConfig}) and by {@link PreAuthorize} for defence in
 * depth. A USER receives 403.
 */
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/wallets")
    public ResponseEntity<PageResponse<AdminWalletResponse>> getAllWallets(Pageable pageable) {
        return ResponseEntity.ok(adminService.getAllWallets(pageable));
    }

    @GetMapping("/transactions")
    public ResponseEntity<PageResponse<TransactionResponse>> getAllTransactions(Pageable pageable) {
        return ResponseEntity.ok(adminService.getAllTransactions(pageable));
    }
}
