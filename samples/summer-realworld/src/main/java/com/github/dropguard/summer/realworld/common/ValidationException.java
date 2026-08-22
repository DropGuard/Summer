package com.github.dropguard.summer.realworld.common;

import com.github.dropguard.summer.web.HttpStatus;

/**
 * Thrown when request input fails validation (e.g. blank username). Maps to HTTP 422. Carries the
 * offending {@code field} so the response can point at the specific input.
 */
public class ValidationException extends BusinessException {

    public ValidationException(String field, String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed", field, message);
    }
}
