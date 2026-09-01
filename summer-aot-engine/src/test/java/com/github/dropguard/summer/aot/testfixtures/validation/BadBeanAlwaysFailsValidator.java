package com.github.dropguard.summer.aot.testfixtures.validation;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.core.validation.Result;
import com.github.dropguard.summer.core.validation.Validator;

/**
 * Companion fixture to {@link BadBeanValidator}: a second Validator that always reports a single
 * object-level violation, so a multi-validator test can assert that the generated {@code build()}
 * runs <em>every</em> Validator (not just the first one) and accumulates their violations into the
 * single shared {@code Result}.
 */
@Component
public class BadBeanAlwaysFailsValidator implements Validator<BadBean> {

    @Override
    public Class<BadBean> targetType() {
        return BadBean.class;
    }

    @Override
    public void validate(BadBean bean, Result result) {
        result.violate("", "always fails (test fixture)");
    }
}
