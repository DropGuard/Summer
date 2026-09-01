package com.github.dropguard.summer.aot.testfixtures.validation;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.core.validation.Result;
import com.github.dropguard.summer.core.validation.Validator;

/**
 * Test fixture: a {@link Validator} that accumulates two violations when {@link BadBean#name()} is
 * null or blank. Used by the AOT validation-phase codegen test to assert that:
 *
 * <ul>
 *   <li>the generated {@code build()} walks every Validator bean in {@code builder.singletons()},
 *   <li>{@code validate(target, result)} is called via the {@code (Validator<Object>)} unchecked
 *       cast,
 *   <li>{@code result.throwIfInvalid()} fires once after the loop and surfaces every violation via
 *       {@link com.github.dropguard.summer.core.exception.ConfigValidationException}.
 * </ul>
 *
 * <p>Two distinct violations are accumulated (path {@code "name"} with two different messages) to
 * prove the failure-fast contract is gone: both must appear in the resulting exception's {@code
 * violations()} list.
 */
@Component
public class BadBeanValidator implements Validator<BadBean> {

    @Override
    public Class<BadBean> targetType() {
        return BadBean.class;
    }

    @Override
    public void validate(BadBean bean, Result result) {
        if (bean == null) {
            result.violate("", "BadBean cannot be null");
            return;
        }
        if (bean.name() == null) {
            result.violate("name", "name is required");
        }
        if (bean.name() != null && bean.name().isBlank()) {
            result.violate("name", "name must not be blank");
        }
    }
}
