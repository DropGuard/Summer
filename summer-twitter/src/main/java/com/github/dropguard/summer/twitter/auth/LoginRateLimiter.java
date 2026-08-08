package com.github.dropguard.summer.twitter.auth;

import com.github.dropguard.summer.core.Component;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory login rate limiter — prevents brute-force attacks on {@code POST
 * /api/auth/login}.
 *
 * <p>Policy: 5 failed attempts per username within a 15-minute sliding window. After the threshold
 * is reached, further attempts are rejected with HTTP 429 until the window resets. A successful
 * login clears the counter.
 */
@Component
public class LoginRateLimiter {

    static final int MAX_ATTEMPTS = 5;
    static final long WINDOW_SECONDS = 15 * 60;
    private static final int MAX_ENTRIES = 10_000;

    private final ConcurrentHashMap<String, FailureRecord> attempts = new ConcurrentHashMap<>();

    record FailureRecord(int count, long windowStart) {}

    /** Whether the given username is currently blocked. */
    public boolean isBlocked(String username) {
        FailureRecord rec = attempts.get(username);
        if (rec == null) return false;
        if (isExpired(rec)) {
            attempts.remove(username, rec);
            return false;
        }
        return rec.count() >= MAX_ATTEMPTS;
    }

    /** Record a failed login attempt for the given username. */
    public void recordFailure(String username) {
        if (attempts.size() > MAX_ENTRIES) {
            attempts.entrySet().removeIf(e -> isExpired(e.getValue()));
        }
        attempts.compute(
                username,
                (k, existing) -> {
                    if (existing == null || isExpired(existing)) {
                        return new FailureRecord(1, now());
                    }
                    return new FailureRecord(existing.count() + 1, existing.windowStart());
                });
    }

    /** Reset the counter after a successful login. */
    public void reset(String username) {
        attempts.remove(username);
    }

    private boolean isExpired(FailureRecord rec) {
        return now() - rec.windowStart() > WINDOW_SECONDS;
    }

    private static long now() {
        return Instant.now().getEpochSecond();
    }
}
