package com.github.dropguard.summer.realworld.auth;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoginRateLimiterTest {

	private LoginRateLimiter limiter;

	@BeforeEach
	void setUp() {
		limiter = new LoginRateLimiter();
	}

	@Test
	void shouldAllowFirstAttempts() {
		assertFalse(limiter.isBlocked("user@test.com"));
	}

	@Test
	void shouldBlockAfterMaxAttempts() {
		for (int i = 0; i < LoginRateLimiter.MAX_ATTEMPTS; i++) {
			limiter.recordFailure("user@test.com");
		}

		assertTrue(limiter.isBlocked("user@test.com"));
	}

	@Test
	void shouldNotBlockBelowMax() {
		for (int i = 0; i < LoginRateLimiter.MAX_ATTEMPTS - 1; i++) {
			limiter.recordFailure("user@test.com");
		}

		assertFalse(limiter.isBlocked("user@test.com"));
	}

	@Test
	void shouldResetAfterSuccess() {
		for (int i = 0; i < LoginRateLimiter.MAX_ATTEMPTS; i++) {
			limiter.recordFailure("user@test.com");
		}
		assertTrue(limiter.isBlocked("user@test.com"));

		limiter.reset("user@test.com");

		assertFalse(limiter.isBlocked("user@test.com"));
	}

	@Test
	void shouldNotBlockOtherAccounts() {
		for (int i = 0; i < LoginRateLimiter.MAX_ATTEMPTS; i++) {
			limiter.recordFailure("attacker@test.com");
		}

		assertTrue(limiter.isBlocked("attacker@test.com"));
		assertFalse(limiter.isBlocked("legit@test.com"));
	}
}
