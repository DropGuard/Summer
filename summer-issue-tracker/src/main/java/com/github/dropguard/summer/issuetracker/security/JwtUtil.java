package com.github.dropguard.summer.issuetracker.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import com.github.dropguard.summer.core.Component;

import javax.crypto.SecretKey;

import java.nio.charset.StandardCharsets;

/**
 * Thin JWT helper. Not part of the framework — the demo owns auth, the framework
 * only provides the HTTP plumbing and the DI container that wires this bean.
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expirationMs;

    public JwtUtil(JwtProperties jwtProperties) {
        this.key = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
        this.expirationMs = jwtProperties.expirationMs() > 0 ? jwtProperties.expirationMs() : 86400000L;
    }

    public String generateAccessToken(Long userId, String username) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("type", "access")
                .issuedAt(new java.util.Date())
                .expiration(new java.util.Date(System.currentTimeMillis() + expirationMs))
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(Long userId) {
        long refreshExpiration = 7 * 24 * 60 * 60 * 1000L; // 7 days
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type", "refresh")
                .issuedAt(new java.util.Date())
                .expiration(new java.util.Date(System.currentTimeMillis() + refreshExpiration))
                .signWith(key)
                .compact();
    }

    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** Validates an access token and returns the user ID. */
    public Long validateAccessToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        Claims claims;
        try {
            claims = extractClaims(token);
        } catch (ExpiredJwtException e) {
            return null;
        } catch (Exception e) {
            return null;
        }
        if (!"access".equals(claims.get("type", String.class))) {
            return null;
        }
        if (claims.getExpiration().before(new java.util.Date())) {
            return null;
        }
        return Long.valueOf(claims.getSubject());
    }

    /** Validates a refresh token and returns the user ID. */
    public Long validateRefreshToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        Claims claims;
        try {
            claims = extractClaims(token);
        } catch (ExpiredJwtException e) {
            return null;
        } catch (Exception e) {
            return null;
        }
        if (!"refresh".equals(claims.get("type", String.class))) {
            return null;
        }
        if (claims.getExpiration().before(new java.util.Date())) {
            return null;
        }
        return Long.valueOf(claims.getSubject());
    }

    @Deprecated
    public String generate(Long userId, String username) {
        return generateAccessToken(userId, username);
    }
}
