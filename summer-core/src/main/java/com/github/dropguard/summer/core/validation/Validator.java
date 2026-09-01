package com.github.dropguard.summer.core.validation;

/**
 * Validates a bean instance. Implementation is registered as a {@code @Component} and runs against
 * matching beans during the validation phase.
 *
 * <p>Each violation is added to the result; the framework raises the exception once validation is
 * complete — never mid-validation — so a single error report lists every problem, not just the
 * first.
 */
public interface Validator<T> {

    /** Returns the bean type this validator applies to. */
    Class<T> targetType();

    /**
     * Validates the bean, adding violations to the result. Implementations MUST NOT throw — always
     * report via {@code result.violate(...)}.
     */
    void validate(T bean, Result result);
}
