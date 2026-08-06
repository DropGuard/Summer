package com.github.dropguard.summer.core.validation;

import com.github.dropguard.summer.core.ErrorCode;
import com.github.dropguard.summer.core.exception.ConfigurationException;

/** Thrown when a bean fails validation after config-property binding. */
public class ConfigValidationException extends ConfigurationException {

    public ConfigValidationException(String message) {
        super(ErrorCode.CONFIG_VALIDATION_FAILED, message);
    }
}
