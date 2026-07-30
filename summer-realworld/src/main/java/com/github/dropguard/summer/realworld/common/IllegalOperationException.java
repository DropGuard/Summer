package com.github.dropguard.summer.realworld.common;

import com.github.dropguard.summer.web.HttpStatus;

/**
 * Thrown when an operation is rejected by a business rule (e.g. following
 * yourself). Maps to HTTP 422.
 */
public class IllegalOperationException extends BusinessException {

	public IllegalOperationException(String field, String message) {
		super(HttpStatus.UNPROCESSABLE_ENTITY, "illegal_operation", field, message);
	}
}
