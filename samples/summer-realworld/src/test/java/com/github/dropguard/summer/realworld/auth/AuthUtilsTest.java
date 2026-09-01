package com.github.dropguard.summer.realworld.auth;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.core.config.ConfigBinder.BindingContext;
import com.github.dropguard.summer.realworld.common.InvalidCredentialsException;
import com.github.dropguard.summer.runtime.RuntimeConfigBinder;
import com.github.dropguard.summer.web.HttpContext;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AuthUtilsTest {

    @BeforeAll
    static void installInterfaceBinder() {}

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        JwtProperties properties =
                new RuntimeConfigBinder()
                        .bind(
                                BindingContext.of(
                                        Map.of(
                                                "jwt.secret",
                                                "test-secret-key-for-unit-tests-32bytes!")),
                                "jwt",
                                JwtProperties.class);
        jwtUtil = new JwtUtil(properties);
    }

    private static HttpContext ctxWithHeader(String headerValue) {
        HttpContext ctx = Mockito.mock(HttpContext.class);
        Mockito.when(ctx.header("Authorization")).thenReturn(headerValue);
        return ctx;
    }

    @Test
    void extractTokenReturnsNullWhenHeaderMissing() {
        HttpContext ctx = ctxWithHeader(null);

        assertNull(AuthUtils.extractToken(ctx));
    }

    @Test
    void extractTokenReturnsTokenWhenValid() {
        HttpContext ctx = ctxWithHeader("Token eyJhbGciOiJIUzUxMiJ9.abc.xyz");

        String token = AuthUtils.extractToken(ctx);

        assertEquals("eyJhbGciOiJIUzUxMiJ9.abc.xyz", token);
    }

    @Test
    void extractTokenReturnsNullWhenPrefixWrong() {
        HttpContext ctx = ctxWithHeader("Bearer eyJhbGciOiJIUzUxMiJ9.abc.xyz");

        assertNull(AuthUtils.extractToken(ctx));
    }

    @Test
    void tryGetCurrentUserIdReturnsNullWhenNoHeader() {
        HttpContext ctx = ctxWithHeader(null);

        assertNull(AuthUtils.tryGetCurrentUserId(ctx, jwtUtil));
    }

    @Test
    void tryGetCurrentUserIdReturnsIdWhenValidToken() {
        String token = jwtUtil.generateAccessToken(7L, "a", "a@b.com");
        HttpContext ctx = ctxWithHeader("Token " + token);

        assertEquals(7L, AuthUtils.tryGetCurrentUserId(ctx, jwtUtil));
    }

    @Test
    void tryGetCurrentUserIdThrowsWhenTokenExpired() {
        // Use a deliberately mangled token that won't parse
        HttpContext ctx = ctxWithHeader("Token garbage");

        assertThrows(
                InvalidCredentialsException.class,
                () -> AuthUtils.tryGetCurrentUserId(ctx, jwtUtil));
    }

    @Test
    void getCurrentUserIdThrowsWhenNoHeader() {
        HttpContext ctx = ctxWithHeader(null);

        assertThrows(
                InvalidCredentialsException.class, () -> AuthUtils.getCurrentUserId(ctx, jwtUtil));
    }

    @Test
    void getCurrentUserIdReturnsIdWhenValidToken() {
        String token = jwtUtil.generateAccessToken(3L, "b", "b@c.com");
        HttpContext ctx = ctxWithHeader("Token " + token);

        assertEquals(3L, AuthUtils.getCurrentUserId(ctx, jwtUtil));
    }

    @Test
    void isAuthenticatedReturnsFalseWhenNoHeader() {
        HttpContext ctx = ctxWithHeader(null);

        assertFalse(AuthUtils.isAuthenticated(ctx, jwtUtil));
    }

    @Test
    void isAuthenticatedReturnsTrueWhenValidToken() {
        String token = jwtUtil.generateAccessToken(5L, "c", "c@d.com");
        HttpContext ctx = ctxWithHeader("Token " + token);

        assertTrue(AuthUtils.isAuthenticated(ctx, jwtUtil));
    }
}
