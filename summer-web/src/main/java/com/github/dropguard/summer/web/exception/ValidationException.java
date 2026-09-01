package com.github.dropguard.summer.web.exception;

import com.github.dropguard.summer.core.validation.Result;
import com.github.dropguard.summer.web.HttpStatus;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Thrown when request body validation fails. Carries every accumulated {@link Result.Violation}
 * from the framework's result-acumulation model, so callers see the complete picture in one error
 * response.
 */
public class ValidationException extends HttpException {

    private final List<Result.Violation> violations;

    public ValidationException(List<Result.Violation> violations) {
        super(HttpStatus.BAD_REQUEST.code(), buildMessage(violations));
        this.violations = List.copyOf(violations);
    }

    /** Returns every validation violation that was accumulated. */
    public List<Result.Violation> violations() {
        return violations;
    }

    private static String buildMessage(List<Result.Violation> violations) {
        return violations.stream()
                .map(v -> v.path().isEmpty() ? v.message() : v.path() + ": " + v.message())
                .collect(Collectors.joining("; "));
    }
}
