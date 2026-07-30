package com.github.dropguard.summer.twitter.auth;

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
		assertFalse(limiter.isBlocked("attacker"));
	}

	@Test
	void shouldBlockAfterMaxAttempts() {
		for (int i = 0; i < LoginRateLimiter.MAX_ATTEMPTS; i++) {
			limiter.recordFailure("attacker");
		}

		assertTrue(limiter.isBlocked("attacker"));
	}

	@Test
	void shouldNotBlockBelowMax() {
		for (int i = 0; i < LoginRateLimiter.MAX_ATTEMPTS - 1; i++) {
			limiter.recordFailure("attacker");
		}

		assertFalse(limiter.isBlocked("attacker"));
	}

	@Test
	void shouldResetAfterSuccess() {
		for (int i = 0; i < LoginRateLimiter.MAX_ATTEMPTS; i++) {
			limiter.recordFailure("attacker");
		}
		assertTrue(limiter.isBlocked("attacker"));

		limiter.reset("attacker");

		assertFalse(limiter.isBlocked("attacker"));
	}

	@Test
	void shouldNotBlockOtherAccounts() {
		for (int i = 0; i < LoginRateLimiter.MAX_ATTEMPTS; i++) {
			limiter.recordFailure("attacker");
		}

		assertTrue(limiter.isBlocked("attacker"));
		assertFalse(limiter.isBlocked("legit_user"));
	}
}
