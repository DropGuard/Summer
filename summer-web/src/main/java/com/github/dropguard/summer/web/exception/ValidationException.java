package com.github.dropguard.summer.web.exception;

import com.github.dropguard.summer.web.HttpStatus;
import java.util.List;

/** Thrown when request body validation fails. */
public class ValidationException extends HttpException {
    private final List<String> errors;

    public ValidationException(List<String> errors) {
        super(HttpStatus.BAD_REQUEST.code(), "Validation failed: " + String.join(", ", errors));
        this.errors = errors;
    }

    public List<String> errors() {
        return errors;
    }
}
