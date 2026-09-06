package com.example.wallet_system.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.wallet_system.dto.request.AddMoneyRequest;
import com.example.wallet_system.dto.request.TransferRequest;
import com.example.wallet_system.dto.response.TransactionResponse;
import com.example.wallet_system.entity.User;
import com.example.wallet_system.enums.Role;
import com.example.wallet_system.exception.InsufficientBalanceException;
import com.example.wallet_system.exception.SelfTransferException;
import com.example.wallet_system.exception.UserNotFoundException;
import com.example.wallet_system.repository.TransactionRepository;
import com.example.wallet_system.repository.WalletRepository;
import com.example.wallet_system.support.TestFixtures;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration tests that exercise the real database so idempotency and balance
 * arithmetic are proven against actual persistence, not mocks.
 */
@SpringBootTest
class WalletServiceIntegrationTest {

    @Autowired private WalletService walletService;
    @Autowired private WalletRepository walletRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private TestFixtures fixtures;

    private User sender;
    private User receiver;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        walletRepository.deleteAll();
        // deleteAll on wallets leaves users; clear via repository through fixtures user repo
        sender = fixtures.createUser(unique("sender"), "Password123", Role.USER, new BigDecimal("1000.00"));
        receiver = fixtures.createUser(unique("receiver"), "Password123", Role.USER, new BigDecimal("0.00"));
    }

    private String unique(String prefix) {
        return prefix + "-" + System.nanoTime() + "@test.local";
    }

    @Test
    void addMoneyIncreasesBalance() {
        TransactionResponse tx = walletService.addMoney(sender.getId(), new AddMoneyRequest(new BigDecimal("250.00")), "add-1");

        assertThat(tx.type()).isEqualTo("ADD");
        assertThat(tx.status()).isEqualTo("SUCCESS");
        assertThat(tx.fromWalletId()).isNull();
        assertThat(balanceOf(sender)).isEqualByComparingTo("1250.00");
    }

    @Test
    void addMoneyIsIdempotent() {
        TransactionResponse first = walletService.addMoney(sender.getId(), new AddMoneyRequest(new BigDecimal("100.00")), "dup-add");
        TransactionResponse second = walletService.addMoney(sender.getId(), new AddMoneyRequest(new BigDecimal("100.00")), "dup-add");

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(balanceOf(sender)).isEqualByComparingTo("1100.00");
        assertThat(transactionRepository.count()).isEqualTo(1);
    }

    @Test
    void transferMovesMoneyAtomically() {
        TransactionResponse tx = walletService.transfer(
            sender.getId(), new TransferRequest(receiver.getId(), new BigDecimal("300.00")), "t-1");

        assertThat(tx.type()).isEqualTo("TRANSFER");
        assertThat(balanceOf(sender)).isEqualByComparingTo("700.00");
        assertThat(balanceOf(receiver)).isEqualByComparingTo("300.00");
    }

    @Test
    void transferIsIdempotent() {
        TransactionResponse first = walletService.transfer(
            sender.getId(), new TransferRequest(receiver.getId(), new BigDecimal("300.00")), "t-dup");
        TransactionResponse second = walletService.transfer(
            sender.getId(), new TransferRequest(receiver.getId(), new BigDecimal("300.00")), "t-dup");

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(balanceOf(sender)).isEqualByComparingTo("700.00");
        assertThat(balanceOf(receiver)).isEqualByComparingTo("300.00");
    }

    @Test
    void transferRejectsInsufficientBalance() {
        assertThatThrownBy(() -> walletService.transfer(
            sender.getId(), new TransferRequest(receiver.getId(), new BigDecimal("5000.00")), "t-insuff"))
            .isInstanceOf(InsufficientBalanceException.class);

        // Nothing committed: balances unchanged and no ledger row.
        assertThat(balanceOf(sender)).isEqualByComparingTo("1000.00");
        assertThat(balanceOf(receiver)).isEqualByComparingTo("0.00");
        assertThat(transactionRepository.findByIdempotencyKey("t-insuff")).isEmpty();
    }

    @Test
    void transferToSelfRejected() {
        assertThatThrownBy(() -> walletService.transfer(
            sender.getId(), new TransferRequest(sender.getId(), new BigDecimal("100.00")), "t-self"))
            .isInstanceOf(SelfTransferException.class);
    }

    @Test
    void transferToNonexistentUserRejected() {
        assertThatThrownBy(() -> walletService.transfer(
            sender.getId(), new TransferRequest(999999L, new BigDecimal("100.00")), "t-nouser"))
            .isInstanceOf(UserNotFoundException.class);
        assertThat(balanceOf(sender)).isEqualByComparingTo("1000.00");
    }

    private BigDecimal balanceOf(User user) {
        return walletRepository.findByUserId(user.getId()).orElseThrow().getBalance();
    }
}
