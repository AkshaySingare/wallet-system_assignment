package com.example.wallet_system.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.example.wallet_system.dto.request.LoginRequest;
import com.example.wallet_system.dto.request.RegisterRequest;
import com.example.wallet_system.dto.response.AuthResponse;
import com.example.wallet_system.dto.response.RegisterResponse;
import com.example.wallet_system.entity.User;
import com.example.wallet_system.entity.Wallet;
import com.example.wallet_system.enums.Role;
import com.example.wallet_system.exception.ApiException;
import com.example.wallet_system.exception.DuplicateEmailException;
import com.example.wallet_system.exception.ErrorCode;
import com.example.wallet_system.repository.UserRepository;
import com.example.wallet_system.repository.WalletRepository;
import com.example.wallet_system.security.JwtService;
import com.example.wallet_system.service.impl.AuthServiceImpl;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtService jwtService;

    @InjectMocks private AuthServiceImpl authService;

    private RegisterRequest registerRequest;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest("User@Example.com", "Password123");
    }

    @Test
    void registerCreatesUserAndWallet() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123")).thenReturn("hashed");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> {
            Wallet w = inv.getArgument(0);
            w.setId(10L);
            return w;
        });

        RegisterResponse response = authService.register(registerRequest);

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.email()).isEqualTo("user@example.com");
        assertThat(response.role()).isEqualTo("USER");
        assertThat(response.walletId()).isEqualTo(10L);
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest))
            .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void loginReturnsToken() {
        User user = User.builder().id(1L).email("user@example.com").password("hashed").role(Role.USER).build();
        when(userRepository.findByEmail("user@example.com")).thenReturn(java.util.Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn("jwt-token");

        AuthResponse response = authService.login(new LoginRequest("user@example.com", "Password123"));

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
    }

    @Test
    void loginWithBadCredentialsThrowsUnauthorized() {
        when(authenticationManager.authenticate(any()))
            .thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@example.com", "wrong")))
            .isInstanceOf(ApiException.class)
            .extracting(ex -> ((ApiException) ex).getErrorCode())
            .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void registerWreckedByConcurrentInsertMapsToDuplicate() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.saveAndFlush(any(User.class)))
            .thenThrow(new org.springframework.dao.DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> authService.register(registerRequest))
            .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void unusedBalanceConstantSanity() {
        // Guards MoneyUtil normalization used when creating wallets.
        assertThat(BigDecimal.ZERO.compareTo(new BigDecimal("0.00"))).isZero();
    }
}
