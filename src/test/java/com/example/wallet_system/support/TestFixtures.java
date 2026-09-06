package com.example.wallet_system.support;

import com.example.wallet_system.entity.User;
import com.example.wallet_system.entity.Wallet;
import com.example.wallet_system.enums.Role;
import com.example.wallet_system.repository.UserRepository;
import com.example.wallet_system.repository.WalletRepository;
import com.example.wallet_system.util.MoneyUtil;
import java.math.BigDecimal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Helper to create users + wallets directly in the DB for integration tests. */
@Component
public class TestFixtures {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;

    public TestFixtures(
        UserRepository userRepository, WalletRepository walletRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User createUser(String email, String rawPassword, Role role, BigDecimal balance) {
        User user = userRepository.save(User.builder()
            .email(email.toLowerCase())
            .password(passwordEncoder.encode(rawPassword))
            .role(role)
            .build());
        walletRepository.save(Wallet.builder()
            .user(user)
            .balance(MoneyUtil.normalize(balance))
            .build());
        return user;
    }
}
