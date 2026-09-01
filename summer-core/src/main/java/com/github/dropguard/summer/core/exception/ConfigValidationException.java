package com.github.dropguard.summer.core.exception;

import com.github.dropguard.summer.core.ErrorCode;
import com.github.dropguard.summer.core.validation.Result;
import java.util.List;

/**
 * Aggregated validation failure raised by the framework's validation phase. Carries every {@link
 * Result.Violation} collected across all {@code Validator} beans, so callers see the complete
 * picture of failures rather than the first one (the old fail-fast contract).
 *
 * <p>Extends {@link SummerException} directly — not {@link ConfigurationException} — because the
 * validation phase no longer only validates configuration: {@code Validator} beans may target
 * config products, business services, or any other bean in the container.
 *
 * <p>Always thrown after the validation phase completes, never mid-validation, so a single error
 * report lists every problem.
 */
public class ConfigValidationException extends SummerException {

    private final List<Result.Violation> violations;

    /**
     * Wraps every collected {@link Result.Violation} into a single message. The message format is
     * "{path}: {message}" for field violations, or just "{message}" for object-level violations
     * (where {@code path} is "").
     */
    public ConfigValidationException(List<Result.Violation> violations) {
        super(ErrorCode.CONFIG_VALIDATION_FAILED, buildMessage(violations));
        this.violations = List.copyOf(violations);
    }

    /** Returns every violation that was accumulated. */
    public List<Result.Violation> violations() {
        return violations;
    }

    /** Builds the combined error message from all violations. */
    private static String buildMessage(List<Result.Violation> violations) {
        String joined =
                violations.stream()
                        .map(v -> v.path().isEmpty() ? v.message() : v.path() + ": " + v.message())
                        .collect(java.util.stream.Collectors.joining("; "));
        return joined.isEmpty() ? "Validation failed" : joined;
    }
}
