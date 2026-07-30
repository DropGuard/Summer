package com.github.dropguard.summer.twitter.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.twitter.common.BusinessException;
import com.github.dropguard.summer.web.HttpStatus;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey key;
    private static final long ACCESS_EXPIRATION = 15 * 60 * 1000L; // 15 minutes
    private static final long REFRESH_EXPIRATION = 7 * 24 * 60 * 60 * 1000L; // 7 days

    public JwtUtil(JwtProperties jwtProperties) {
        this.key = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(Long userId, String username) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("type", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ACCESS_EXPIRATION))
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(Long userId) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type", "refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + REFRESH_EXPIRATION))
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
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "TOKEN_MISSING", "Token is missing");
        }
        Claims claims;
        try {
            claims = extractClaims(token);
        } catch (ExpiredJwtException e) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED", "Token is expired");
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "TOKEN_INVALID", "Token is invalid");
        }
        if (!"access".equals(claims.get("type", String.class))) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "TOKEN_INVALID", "Token is invalid");
        }
        if (claims.getExpiration().before(new Date())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED", "Token is expired");
        }
        return Long.valueOf(claims.getSubject());
    }

    /** Validates a refresh token and returns the user ID. */
    public Long validateRefreshToken(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "TOKEN_MISSING", "Token is missing");
        }
        Claims claims;
        try {
            claims = extractClaims(token);
        } catch (ExpiredJwtException e) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED", "Token is expired");
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "TOKEN_INVALID", "Token is invalid");
        }
        if (!"refresh".equals(claims.get("type", String.class))) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "TOKEN_INVALID", "Token is invalid");
        }
        if (claims.getExpiration().before(new Date())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED", "Token is expired");
        }
        return Long.valueOf(claims.getSubject());
    }

    public boolean validate(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
