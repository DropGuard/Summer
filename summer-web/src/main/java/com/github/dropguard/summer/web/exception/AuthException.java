package com.github.dropguard.summer.web.exception;

import com.github.dropguard.summer.web.HttpStatus;

/** Thrown when authentication or authorization fails. */
public class AuthException extends HttpException {
    public AuthException(int status, String message) {

        super(status, message);
    }

    /** Convenience for unauthorized (401) */
    public AuthException(String message) {
        super(HttpStatus.UNAUTHORIZED.code(), message);
    }

    /** Convenience for forbidden (403) */
    public static AuthException forbidden(String message) {
        return new AuthException(HttpStatus.FORBIDDEN.code(), message);
    }

    /** Convenience for unauthorized (401) */
    public static AuthException unauthorized(String message) {
        return new AuthException(HttpStatus.UNAUTHORIZED.code(), message);
    }
}
