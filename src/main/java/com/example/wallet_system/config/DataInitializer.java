package com.example.wallet_system.config;

import com.example.wallet_system.entity.User;
import com.example.wallet_system.entity.Wallet;
import com.example.wallet_system.enums.Role;
import com.example.wallet_system.repository.UserRepository;
import com.example.wallet_system.repository.WalletRepository;
import com.example.wallet_system.util.MoneyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds a single ADMIN account on startup so the admin endpoints are usable
 * out of the box. Registration only ever creates USERs, so an admin must be
 * provisioned this way. Credentials come from configuration; the local fallback
 * is documented in README and must be overridden in any shared environment.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public DataInitializer(
        UserRepository userRepository,
        WalletRepository walletRepository,
        PasswordEncoder passwordEncoder,
        @Value("${app.admin.email:admin@wallet.local}") String adminEmail,
        @Value("${app.admin.password:Admin@12345}") String adminPassword) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(String... args) {
        String email = adminEmail.trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            return;
        }
        User admin = userRepository.save(User.builder()
            .email(email)
            .password(passwordEncoder.encode(adminPassword))
            .role(Role.ADMIN)
            .build());
        walletRepository.save(Wallet.builder()
            .user(admin)
            .balance(MoneyUtil.ZERO)
            .build());
        log.info("Seeded ADMIN account: {}", email);
    }
}
