package com.github.dropguard.summer.realworld.auth;

import com.github.dropguard.summer.core.Component;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory login rate limiter — prevents brute-force attacks on {@code POST
 * /api/users/login}.
 *
 * <p>Policy: 5 failed attempts per email within a 15-minute sliding window. After the threshold is
 * reached, further attempts are rejected with HTTP 429 until the window resets. A successful login
 * clears the counter.
 */
@Component
public class LoginRateLimiter {

    static final int MAX_ATTEMPTS = 5;
    static final long WINDOW_SECONDS = 15 * 60;

    private final ConcurrentHashMap<String, FailureRecord> attempts = new ConcurrentHashMap<>();

    record FailureRecord(int count, long windowStart) {}

    /** Whether the given email is currently blocked. */
    public boolean isBlocked(String email) {
        FailureRecord rec = attempts.get(email);
        if (rec == null) return false;
        if (isExpired(rec)) {
            attempts.remove(email, rec);
            return false;
        }
        return rec.count() >= MAX_ATTEMPTS;
    }

    /** Record a failed login attempt for the given email. */
    public void recordFailure(String email) {
        attempts.compute(
                email,
                (k, existing) -> {
                    if (existing == null || isExpired(existing)) {
                        return new FailureRecord(1, now());
                    }
                    return new FailureRecord(existing.count() + 1, existing.windowStart());
                });
    }

    /** Reset the counter after a successful login. */
    public void reset(String email) {
        attempts.remove(email);
    }

    private boolean isExpired(FailureRecord rec) {
        return now() - rec.windowStart() > WINDOW_SECONDS;
    }

    private static long now() {
        return Instant.now().getEpochSecond();
    }
}
