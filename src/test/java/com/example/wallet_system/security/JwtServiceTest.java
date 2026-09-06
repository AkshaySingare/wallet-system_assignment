package com.example.wallet_system.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.wallet_system.entity.User;
import com.example.wallet_system.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-that-is-long-enough-for-hs256-000000";

    @Test
    void generatedTokenCarriesIdentityClaims() {
        JwtService service = new JwtService(SECRET, 3600000L);
        User user = User.builder().id(42L).email("a@b.com").role(Role.ADMIN).build();

        String token = service.generateToken(user);
        Claims claims = service.parse(token);

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("email", String.class)).isEqualTo("a@b.com");
        assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService service = new JwtService(SECRET, -1000L); // already expired
        User user = User.builder().id(1L).email("a@b.com").role(Role.USER).build();
        String token = service.generateToken(user);

        assertThatThrownBy(() -> service.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenSignedWithDifferentSecretIsRejected() {
        JwtService issuer = new JwtService(SECRET, 3600000L);
        JwtService other = new JwtService("a-totally-different-secret-key-long-enough-000000", 3600000L);
        String token = issuer.generateToken(User.builder().id(1L).email("a@b.com").role(Role.USER).build());

        assertThatThrownBy(() -> other.parse(token)).isInstanceOf(JwtException.class);
    }
}
