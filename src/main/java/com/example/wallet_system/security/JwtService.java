package com.example.wallet_system.security;

import com.example.wallet_system.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Issues and validates HS256 JWTs. The token subject is the user id and a
 * {@code role} claim carries the authority, so a protected request can be
 * authenticated without a database lookup for the identity itself.
 *
 * <p>The signing secret is supplied via configuration ({@code app.jwt.secret})
 * and never hardcoded in source.
 */
@Service
public class JwtService {

    private final Key signingKey;
    private final long expirationMillis;

    public JwtService(
        @Value("${app.jwt.secret}") String secret,
        @Value("${app.jwt.expiration-ms}") long expirationMillis) {
        // HS256 requires a key of at least 256 bits; the configured secret must
        // be long enough (documented in README / application.yml).
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMillis = expirationMillis;
    }

    public String generateToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMillis);
        return Jwts.builder()
            .setSubject(String.valueOf(user.getId()))
            .claim("email", user.getEmail())
            .claim("role", user.getRole().name())
            .setIssuedAt(now)
            .setExpiration(expiry)
            .signWith(signingKey)
            .compact();
    }

    /**
     * Parse and validate a token, returning its claims. Throws a jjwt
     * {@code JwtException} (incl. {@code ExpiredJwtException}) if invalid; the
     * authentication filter treats any such failure as an unauthenticated
     * request.
     */
    public Claims parse(String token) {
        return Jwts.parserBuilder()
            .setSigningKey(signingKey)
            .build()
            .parseClaimsJws(token)
            .getBody();
    }
}
