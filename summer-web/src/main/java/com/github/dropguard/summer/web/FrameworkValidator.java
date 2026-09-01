package com.github.dropguard.summer.web;

import com.github.dropguard.summer.core.validation.Result;
import com.github.dropguard.summer.core.validation.Validator;
import io.avaje.validation.ConstraintViolation;
import io.avaje.validation.ConstraintViolationException;

/**
 * Bridge adapter that wraps avaje's {@code io.avaje.validation.Validator} into Summer's {@code
 * Validator} interface using the result-acumulation model.
 *
 * <p>This adapter exists only to allow reuse of avaje's validation engine while providing the
 * framework's unified validation error reporting. The avaje validator throws on first violation;
 * this adapter catches that exception, extracts every violation from the avae result, and adds them
 * to Summer's {@link Result} accumulator.
 *
 * <p>If the avaje validator reports zero violations, the result remains valid and nothing is
 * thrown.
 */
final class FrameworkValidator implements Validator<Object> {

    private final io.avaje.validation.Validator delegate =
            io.avaje.validation.Validator.builder().build();

    @Override
    public Class<Object> targetType() {
        return Object.class; // accepts any type
    }

    @Override
    public void validate(Object bean, Result result) {
        try {
            delegate.validate(bean);
        } catch (ConstraintViolationException e) {
            for (ConstraintViolation v : e.violations()) {
                String path = v.path();
                if (path == null) path = v.field();
                String message = v.message();
                result.violate(path == null ? "" : path, message);
            }
        }
    }
}
