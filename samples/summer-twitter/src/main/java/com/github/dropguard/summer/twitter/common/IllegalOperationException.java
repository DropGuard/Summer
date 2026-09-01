package com.github.dropguard.summer.twitter.common;

import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.exception.AuthException;

/** Thrown when an operation is not allowed (e.g., following yourself). */
public class IllegalOperationException extends AuthException {
    public IllegalOperationException(String message) {
        super(HttpStatus.FORBIDDEN.code(), message);
    }
}
