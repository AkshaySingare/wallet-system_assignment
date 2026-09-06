package com.example.wallet_system.service.impl;

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
import com.example.wallet_system.service.AuthService;
import com.example.wallet_system.util.MoneyUtil;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthServiceImpl(
        UserRepository userRepository,
        WalletRepository walletRepository,
        PasswordEncoder passwordEncoder,
        AuthenticationManager authenticationManager,
        JwtService jwtService) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    /**
     * Register a new user and atomically create their (single) zero-balance
     * wallet in the same transaction. The unique constraint on {@code email} is
     * the authoritative duplicate guard; the pre-check simply yields a friendlier
     * error for the common case.
     */
    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException(email);
        }

        User user = User.builder()
            .email(email)
            .password(passwordEncoder.encode(request.password()))
            .role(Role.USER)
            .build();

        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            // Lost the race against a concurrent registration with the same email.
            throw new DuplicateEmailException(email);
        }

        Wallet wallet = Wallet.builder()
            .user(user)
            .balance(MoneyUtil.ZERO)
            .build();
        wallet = walletRepository.save(wallet);

        return new RegisterResponse(user.getId(), user.getEmail(), user.getRole().name(), wallet.getId());
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (AuthenticationException ex) {
            // Uniform message: do not reveal whether the email exists.
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Invalid email or password");
        }

        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "Invalid email or password"));
        return AuthResponse.bearer(jwtService.generateToken(user));
    }
}
