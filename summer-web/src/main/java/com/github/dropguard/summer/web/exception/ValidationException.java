package com.github.dropguard.summer.web.exception;

import com.github.dropguard.summer.core.ErrorCode;
import com.github.dropguard.summer.web.HttpStatus;
import java.util.List;

/**
 * Thrown when request body validation fails.
 */
public class ValidationException extends SummerWebException {
	private final List<String> errors;

	public ValidationException(List<String> errors) {
		super(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Validation failed: " + String.join(", ", errors));
		this.errors = errors;
	}

	public List<String> errors() {
		return errors;
	}
}
