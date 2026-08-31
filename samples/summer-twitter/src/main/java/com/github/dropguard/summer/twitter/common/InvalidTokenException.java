package com.github.dropguard.summer.twitter.common;

import com.github.dropguard.summer.web.exception.AuthException;

/** Thrown when the provided token is invalid or expired. */
public class InvalidTokenException extends AuthException {
    public InvalidTokenException(String message) {
        super(message);
    }
}
