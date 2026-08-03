package com.github.dropguard.summer.realworld.auth;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.core.config.ConfigBinder.BindingContext;
import com.github.dropguard.summer.core.config.ConfigBinder;
import com.github.dropguard.summer.realworld.common.BusinessException;
import io.jsonwebtoken.Jwts;
import java.util.Date;
import java.util.Map;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtUtilTest {

	@BeforeAll
	static void installInterfaceBinder() {
		// Interface config binding (@ConfigMapping) is provided by the runtime module.
	}

	private JwtUtil jwtUtil;

	@BeforeEach
	void setUp() {
		JwtProperties properties =
				new ConfigBinder().bind(
						BindingContext.of(Map.of("jwt.secret", "test-secret-key-for-unit-tests-32bytes!")),
						"jwt",
						JwtProperties.class);
		jwtUtil = new JwtUtil(properties);
	}

	@Test
	void shouldGenerateAccessToken() {
		String token = jwtUtil.generateAccessToken(1L, "testuser", "test@example.com");

		assertNotNull(token);
		assertFalse(token.isEmpty());
		assertTrue(jwtUtil.isAccessToken(token));
	}

	@Test
	void shouldGenerateRefreshToken() {
		String token = jwtUtil.generateRefreshToken(1L);

		assertNotNull(token);
		assertFalse(token.isEmpty());
		assertTrue(jwtUtil.isRefreshToken(token));
	}

	@Test
	void shouldGetUserIdFromToken() {
		String token = jwtUtil.generateAccessToken(42L, "testuser", "test@example.com");

		Long userId = jwtUtil.getUserIdFromToken(token);

		assertEquals(42L, userId);
	}

	@Test
	void shouldGetUsernameFromToken() {
		String token = jwtUtil.generateAccessToken(1L, "testuser", "test@example.com");

		String username = jwtUtil.getUsernameFromToken(token);

		assertEquals("testuser", username);
	}

	@Test
	void shouldGetEmailFromToken() {
		String token = jwtUtil.generateAccessToken(1L, "testuser", "test@example.com");

		String email = jwtUtil.getEmailFromToken(token);

		assertEquals("test@example.com", email);
	}

	@Test
	void shouldIdentifyTokenType() {
		String accessToken = jwtUtil.generateAccessToken(1L, "user", "user@test.com");
		String refreshToken = jwtUtil.generateRefreshToken(1L);

		assertTrue(jwtUtil.isAccessToken(accessToken));
		assertFalse(jwtUtil.isRefreshToken(accessToken));

		assertTrue(jwtUtil.isRefreshToken(refreshToken));
		assertFalse(jwtUtil.isAccessToken(refreshToken));
	}

	@Test
	void shouldDetectNonExpiredToken() {
		String token = jwtUtil.generateAccessToken(1L, "user", "user@test.com");

		assertFalse(jwtUtil.isTokenExpired(token));
	}

	// ── validateAccessToken ──────────────────────────────────────────

	@Test
	void validateAccessTokenReturnsUserIdForValidToken() {
		String token = jwtUtil.generateAccessToken(99L, "user", "user@test.com");

		Long userId = jwtUtil.validateAccessToken(token);

		assertEquals(99L, userId);
	}

	@Test
	void validateAccessTokenThrowsOnNullToken() {
		BusinessException ex = assertThrows(BusinessException.class,
				() -> jwtUtil.validateAccessToken(null));
		assertEquals("token", ex.field());
		assertEquals("is missing", ex.getMessage());
	}

	@Test
	void validateAccessTokenThrowsOnBlankToken() {
		BusinessException ex = assertThrows(BusinessException.class,
				() -> jwtUtil.validateAccessToken("   "));
		assertEquals("token", ex.field());
		assertEquals("is missing", ex.getMessage());
	}

	@Test
	void validateAccessTokenThrowsOnGarbageToken() {
		BusinessException ex = assertThrows(BusinessException.class,
				() -> jwtUtil.validateAccessToken("not-a-jwt"));
		assertEquals("token", ex.field());
		assertEquals("is invalid", ex.getMessage());
	}

	@Test
	void validateAccessTokenThrowsOnExpiredToken() throws Exception {
		// Build an already-expired token directly so we don't have to sleep
		SecretKey key = io.jsonwebtoken.security.Keys.hmacShaKeyFor(
				"test-secret-key-for-unit-tests-32bytes!".getBytes());
		String expiredToken = Jwts.builder()
				.subject("1")
				.claim("type", "access")
				.issuedAt(new Date(System.currentTimeMillis() - 3600_000))
				.expiration(new Date(System.currentTimeMillis() - 1))
				.signWith(key)
				.compact();

		BusinessException ex = assertThrows(BusinessException.class,
				() -> jwtUtil.validateAccessToken(expiredToken));
		assertEquals("token", ex.field());
		assertEquals("is expired", ex.getMessage());
	}

	@Test
	void validateAccessTokenThrowsOnRefreshTokenUsedAsAccess() {
		String refreshToken = jwtUtil.generateRefreshToken(1L);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> jwtUtil.validateAccessToken(refreshToken));
		assertEquals("token", ex.field());
		assertEquals("is invalid", ex.getMessage());
	}

	// ── validateRefreshToken ─────────────────────────────────────────

	@Test
	void validateRefreshTokenReturnsUserIdForValidToken() {
		String token = jwtUtil.generateRefreshToken(42L);

		Long userId = jwtUtil.validateRefreshToken(token);

		assertEquals(42L, userId);
	}

	@Test
	void validateRefreshTokenRejectsAccessToken() {
		String accessToken = jwtUtil.generateAccessToken(1L, "u", "u@t.com");

		BusinessException ex = assertThrows(BusinessException.class,
				() -> jwtUtil.validateRefreshToken(accessToken));
		assertEquals("token", ex.field());
		assertEquals("is invalid", ex.getMessage());
	}
}
