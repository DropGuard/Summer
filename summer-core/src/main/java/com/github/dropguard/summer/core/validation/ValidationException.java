package com.github.dropguard.summer.core.validation;

import com.github.dropguard.summer.core.ErrorCode;
import com.github.dropguard.summer.core.exception.ConfigurationException;

/**
 * Thrown when a bean fails validation after property binding.
 */
public class ValidationException extends ConfigurationException {

	public ValidationException(String message) {
		super(ErrorCode.CONFIG_VALIDATION_FAILED, message);
	}
}
