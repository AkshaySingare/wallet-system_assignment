package com.example.wallet_system.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.wallet_system.dto.request.TransferRequest;
import com.example.wallet_system.entity.User;
import com.example.wallet_system.enums.Role;
import com.example.wallet_system.repository.TransactionRepository;
import com.example.wallet_system.repository.WalletRepository;
import com.example.wallet_system.support.TestFixtures;
import java.math.BigDecimal;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Real multithreaded tests proving database-level concurrency and idempotency.
 * These deliberately use the real DB (no mocks) since they exist to prove the
 * locking/constraint behavior.
 */
@SpringBootTest
class WalletConcurrencyTest {

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
        sender = fixtures.createUser(unique("sender"), "Password123", Role.USER, new BigDecimal("1000.00"));
        receiver = fixtures.createUser(unique("receiver"), "Password123", Role.USER, new BigDecimal("0.00"));
    }

    private String unique(String prefix) {
        return prefix + "-" + System.nanoTime() + "@test.local";
    }

    /**
     * Balance = 1000. Two concurrent transfers of 800. Exactly one must succeed;
     * the other must fail cleanly. Final balances must be exact and never negative.
     */
    @Test
    void concurrentTransfersAgainstSameSenderAllowOnlyAffordableOnes() throws Exception {
        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        Callable<Void> task = () -> {
            start.await();
            try {
                walletService.transfer(sender.getId(),
                    new TransferRequest(receiver.getId(), new BigDecimal("800.00")),
                    "key-" + Thread.currentThread().getId() + "-" + System.nanoTime());
                successes.incrementAndGet();
            } catch (RuntimeException ex) {
                failures.incrementAndGet();
            }
            return null;
        };

        Future<Void> f1 = pool.submit(task);
        Future<Void> f2 = pool.submit(task);
        start.countDown();
        f1.get(15, TimeUnit.SECONDS);
        f2.get(15, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(successes.get()).isEqualTo(1);
        assertThat(failures.get()).isEqualTo(1);
        assertThat(walletRepository.findByUserId(sender.getId()).orElseThrow().getBalance())
            .isEqualByComparingTo("200.00");
        assertThat(walletRepository.findByUserId(receiver.getId()).orElseThrow().getBalance())
            .isEqualByComparingTo("800.00");
        // Sender balance must never be negative.
        assertThat(walletRepository.findByUserId(sender.getId()).orElseThrow().getBalance())
            .isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    /**
     * Same idempotency key sent concurrently. The money must move exactly once.
     */
    @Test
    void concurrentDuplicateIdempotencyKeyProcessesOnce() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        String key = "shared-idem-key";
        AtomicInteger ok = new AtomicInteger();

        Callable<Void> task = () -> {
            start.await();
            try {
                walletService.transfer(sender.getId(),
                    new TransferRequest(receiver.getId(), new BigDecimal("100.00")), key);
                ok.incrementAndGet();
            } catch (RuntimeException ignored) {
                // conflicts are acceptable; correctness is asserted on balances/rows
            }
            return null;
        };

        Future<?>[] futures = new Future<?>[threads];
        for (int i = 0; i < threads; i++) {
            futures[i] = pool.submit(task);
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get(15, TimeUnit.SECONDS);
        }
        pool.shutdown();

        // Exactly one ledger row for the key, and money moved exactly once.
        assertThat(transactionRepository.findByIdempotencyKey(key)).isPresent();
        assertThat(transactionRepository.count()).isEqualTo(1);
        assertThat(walletRepository.findByUserId(sender.getId()).orElseThrow().getBalance())
            .isEqualByComparingTo("900.00");
        assertThat(walletRepository.findByUserId(receiver.getId()).orElseThrow().getBalance())
            .isEqualByComparingTo("100.00");
    }

    /**
     * Many concurrent affordable transfers must sum correctly with no lost updates.
     */
    @Test
    void manyConcurrentTransfersProduceCorrectTotals() throws Exception {
        int count = 10;
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);

        Callable<Void> task = () -> {
            start.await();
            walletService.transfer(sender.getId(),
                new TransferRequest(receiver.getId(), new BigDecimal("50.00")),
                "seq-" + Thread.currentThread().getId() + "-" + System.nanoTime());
            return null;
        };

        Future<?>[] futures = new Future<?>[count];
        for (int i = 0; i < count; i++) {
            futures[i] = pool.submit(task);
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get(20, TimeUnit.SECONDS);
        }
        pool.shutdown();

        // 10 x 50 = 500 moved.
        assertThat(walletRepository.findByUserId(sender.getId()).orElseThrow().getBalance())
            .isEqualByComparingTo("500.00");
        assertThat(walletRepository.findByUserId(receiver.getId()).orElseThrow().getBalance())
            .isEqualByComparingTo("500.00");
    }
}
