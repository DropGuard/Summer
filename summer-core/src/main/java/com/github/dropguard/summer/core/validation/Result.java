package com.github.dropguard.summer.core.validation;

import com.github.dropguard.summer.core.exception.ConfigValidationException;
import java.util.ArrayList;
import java.util.List;

/**
 * Mutable accumulator passed to {@link Validator#validate(Object, Result)}.
 *
 * <p>Captures every violation across all validators in one pass — a single error report lists every
 * problem, not just the first. The validation phase raises {@link ConfigValidationException} once
 * {@link #throwIfInvalid()} is called at the end of the run; validators themselves never throw.
 */
public final class Result {

    private final List<Violation> violations = new ArrayList<>();

    /** Adds a violation for a specific property path. */
    public Result violate(String path, String message) {
        violations.add(new Violation(path, message));
        return this;
    }

    /** Adds a violation for the entire object (no property path). */
    public Result violate(String message) {
        return violate("", message);
    }

    public boolean isValid() {
        return violations.isEmpty();
    }

    public List<Violation> violations() {
        return List.copyOf(violations);
    }

    /**
     * Throws {@link ConfigValidationException} with every accumulated violation if {@link
     * #isValid()} returns false. No-op otherwise.
     */
    public void throwIfInvalid() {
        if (!isValid()) {
            throw new ConfigValidationException(violations);
        }
    }

    /** A single property-level (or object-level) validation failure. */
    public record Violation(String path, String message) {}
}
