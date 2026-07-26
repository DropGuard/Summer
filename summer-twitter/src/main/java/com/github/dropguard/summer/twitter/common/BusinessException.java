package com.github.dropguard.summer.twitter.common;

import com.github.dropguard.summer.core.ErrorCode;
import com.github.dropguard.summer.core.exception.SummerException;
import com.github.dropguard.summer.web.HttpStatus;

/**
 * Base class for twitter application exceptions that map to an HTTP response.
 *
 * <p>
 * Every business error carries the {@link HttpStatus} it should produce, so a
 * single {@code @ExceptionHandler(BusinessException.class)} in
 * {@code GlobalErrorHandler} can render it without per-case branching. This is
 * the contract new users should copy: throw a typed exception at the service
 * layer, let it propagate, and let the global handler translate it to a
 * response — never hand-roll {@code try/catch} in controllers.
 * </p>
 */
public class BusinessException extends SummerException {

	private final HttpStatus status;
	private final String code;

	public BusinessException(HttpStatus status, String code, String message) {
		super(ErrorCode.INTERNAL_ERROR, message);
		this.status = status;
		this.code = code;
	}

	public BusinessException(HttpStatus status, String code, String message, Throwable cause) {
		super(ErrorCode.INTERNAL_ERROR, message, cause);
		this.status = status;
		this.code = code;
	}

	public HttpStatus status() {
		return status;
	}

	public String code() {
		return code;
	}
}
