package com.github.dropguard.summer.realworld.auth;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.realworld.common.InvalidCredentialsException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import javax.crypto.SecretKey;

/**
 * JWT utility for token generation and validation.
 *
 * <p>Auto-discovered by the DI container. {@link JwtProperties} is bound automatically from {@code
 * application.yml} via {@code @ConfigMapping}.
 */
@Component
public record JwtUtil(JwtProperties jwtProperties) {

    private static final long ACCESS_TOKEN_EXPIRATION = 15 * 60 * 1000; // 15 minutes
    private static final long REFRESH_TOKEN_EXPIRATION = 7 * 24 * 60 * 60 * 1000; // 7 days

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(jwtProperties.secret().getBytes());
    }

    public String generateAccessToken(Long userId, String username, String email) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("email", email)
                .claim("type", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRATION))
                .signWith(getKey())
                .compact();
    }

    public String generateRefreshToken(Long userId) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type", "refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + REFRESH_TOKEN_EXPIRATION))
                .signWith(getKey())
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser().verifyWith(getKey()).build().parseSignedClaims(token).getPayload();
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return Long.parseLong(claims.getSubject());
    }

    public String getUsernameFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("username", String.class);
    }

    public String getEmailFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("email", String.class);
    }

    public String getTokenType(String token) {
        Claims claims = parseToken(token);
        return claims.get("type", String.class);
    }

    public boolean isTokenExpired(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    public boolean isAccessToken(String token) {
        return "access".equals(getTokenType(token));
    }

    public boolean isRefreshToken(String token) {
        return "refresh".equals(getTokenType(token));
    }

    /**
     * Validates an access token end-to-end and returns the user ID.
     *
     * @throws InvalidCredentialsException 401 with {@code "token"} field if the token is missing,
     *     not an access token, expired, or otherwise unparseable.
     */
    public Long validateAccessToken(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidCredentialsException("token", "is missing");
        }
        String type;
        Claims claims;
        try {
            type = getTokenType(token);
            claims = parseToken(token);
        } catch (ExpiredJwtException e) {
            throw new InvalidCredentialsException("token", "is expired");
        } catch (Exception e) {
            throw new InvalidCredentialsException("token", "is invalid");
        }
        if (!"access".equals(type)) {
            throw new InvalidCredentialsException("token", "is invalid");
        }
        if (claims.getExpiration().before(new Date())) {
            throw new InvalidCredentialsException("token", "is expired");
        }
        return Long.parseLong(claims.getSubject());
    }

    /**
     * Validates a refresh token end-to-end and returns the user ID.
     *
     * @throws InvalidCredentialsException 401 if the token is missing, not a refresh token,
     *     expired, or otherwise unparseable.
     */
    public Long validateRefreshToken(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidCredentialsException("token", "is missing");
        }
        String type;
        Claims claims;
        try {
            type = getTokenType(token);
            claims = parseToken(token);
        } catch (ExpiredJwtException e) {
            throw new InvalidCredentialsException("token", "is expired");
        } catch (Exception e) {
            throw new InvalidCredentialsException("token", "is invalid");
        }
        if (!"refresh".equals(type)) {
            throw new InvalidCredentialsException("token", "is invalid");
        }
        if (claims.getExpiration().before(new Date())) {
            throw new InvalidCredentialsException("token", "is expired");
        }
        return Long.parseLong(claims.getSubject());
    }
}
