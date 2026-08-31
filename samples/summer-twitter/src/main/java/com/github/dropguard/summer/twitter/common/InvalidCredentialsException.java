package com.github.dropguard.summer.twitter.common;

import com.github.dropguard.summer.web.exception.AuthException;

/** Thrown when the provided credentials are invalid. */
public class InvalidCredentialsException extends AuthException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
