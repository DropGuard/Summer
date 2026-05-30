package summer.realworld.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class JwtUtilTest {

	@Test
	void shouldGenerateAccessToken() {
		String token = JwtUtil.generateAccessToken(1L, "testuser", "test@example.com");

		assertNotNull(token);
		assertFalse(token.isEmpty());
		assertTrue(JwtUtil.isAccessToken(token));
	}

	@Test
	void shouldGenerateRefreshToken() {
		String token = JwtUtil.generateRefreshToken(1L);

		assertNotNull(token);
		assertFalse(token.isEmpty());
		assertTrue(JwtUtil.isRefreshToken(token));
	}

	@Test
	void shouldGetUserIdFromToken() {
		String token = JwtUtil.generateAccessToken(42L, "testuser", "test@example.com");

		Long userId = JwtUtil.getUserIdFromToken(token);

		assertEquals(42L, userId);
	}

	@Test
	void shouldGetUsernameFromToken() {
		String token = JwtUtil.generateAccessToken(1L, "testuser", "test@example.com");

		String username = JwtUtil.getUsernameFromToken(token);

		assertEquals("testuser", username);
	}

	@Test
	void shouldGetEmailFromToken() {
		String token = JwtUtil.generateAccessToken(1L, "testuser", "test@example.com");

		String email = JwtUtil.getEmailFromToken(token);

		assertEquals("test@example.com", email);
	}

	@Test
	void shouldIdentifyTokenType() {
		String accessToken = JwtUtil.generateAccessToken(1L, "user", "user@test.com");
		String refreshToken = JwtUtil.generateRefreshToken(1L);

		assertTrue(JwtUtil.isAccessToken(accessToken));
		assertFalse(JwtUtil.isRefreshToken(accessToken));

		assertTrue(JwtUtil.isRefreshToken(refreshToken));
		assertFalse(JwtUtil.isAccessToken(refreshToken));
	}

	@Test
	void shouldDetectNonExpiredToken() {
		String token = JwtUtil.generateAccessToken(1L, "user", "user@test.com");

		assertFalse(JwtUtil.isTokenExpired(token));
	}
}