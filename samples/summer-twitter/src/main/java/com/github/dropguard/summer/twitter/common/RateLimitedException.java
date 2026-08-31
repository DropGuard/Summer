package com.github.dropguard.summer.twitter.common;

import com.github.dropguard.summer.web.exception.TooManyRequestsException;

/** Thrown when the user has exceeded the rate limit for an action (e.g., login attempts). */
public class RateLimitedException extends TooManyRequestsException {
    public RateLimitedException(String message) {
        super(message);
    }
}
