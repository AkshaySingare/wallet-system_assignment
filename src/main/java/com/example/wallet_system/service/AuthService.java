package com.example.wallet_system.service;

import com.example.wallet_system.dto.request.LoginRequest;
import com.example.wallet_system.dto.request.RegisterRequest;
import com.example.wallet_system.dto.response.AuthResponse;
import com.example.wallet_system.dto.response.RegisterResponse;

public interface AuthService {

    RegisterResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);
}
