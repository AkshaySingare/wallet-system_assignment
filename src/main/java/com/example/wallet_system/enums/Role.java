package com.example.wallet_system.enums;

/**
 * Application roles. Persisted on the {@code users} table and mapped to Spring
 * Security authorities as {@code ROLE_USER} / {@code ROLE_ADMIN}.
 */
public enum Role {
    USER,
    ADMIN
}
