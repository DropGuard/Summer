package com.github.dropguard.summer.twitter.common;

import com.github.dropguard.summer.web.exception.AuthException;

/**
 * Base exception for business-level errors in the Twitter module. Extends AuthException to carry
 * HTTP status code.
 */
public class BusinessException extends AuthException {
    private final String code;

    public BusinessException(int status, String code, String message) {
        super(status, message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
