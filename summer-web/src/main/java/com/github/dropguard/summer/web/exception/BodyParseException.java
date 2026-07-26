package com.github.dropguard.summer.web.exception;

import com.github.dropguard.summer.core.ErrorCode;
import com.github.dropguard.summer.web.HttpStatus;

/**
 * Thrown when request body parsing fails.
 */
public class BodyParseException extends SummerWebException {
	public BodyParseException(String converterName, Throwable cause) {
		super(ErrorCode.BODY_PARSE_ERROR, HttpStatus.BAD_REQUEST, "Failed to parse body with " + converterName, cause);
	}
}
