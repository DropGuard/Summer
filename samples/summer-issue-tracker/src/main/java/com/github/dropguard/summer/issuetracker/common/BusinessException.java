package com.github.dropguard.summer.issuetracker.common;

import com.github.dropguard.summer.web.HttpStatus;

/**
 * Base business exception for the issue tracker module.
 */
public class BusinessException extends RuntimeException {
    public static RuntimeException notFound(String what) {
        return new RuntimeException("[" + what + "] not found");
    }

    public BusinessException(HttpStatus status, String code, String message) {
        super("[" + code + "] " + message);
    }
}
