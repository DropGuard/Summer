package summer.realworld.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtUtilTest {

	private JwtUtil jwtUtil;

	@BeforeEach
	void setUp() {
		JwtProperties properties = new JwtProperties("test-secret-key-for-unit-tests-32bytes!");
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
}
