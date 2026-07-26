package com.github.dropguard.summer.realworld.common;

import com.github.dropguard.summer.web.HttpStatus;

/**
 * Base class for realworld application exceptions that map to an HTTP response.
 *
 * <p>
 * Carries the {@link HttpStatus} it should produce, a stable {@code code}, and
 * an optional {@code field} (for field-level validation errors, e.g. which
 * input was rejected). A single {@code @ExceptionHandler(BusinessException)}
 * in {@code GlobalErrorHandler} renders all subtypes, so controllers never
 * hand-write {@code try/catch} for business errors.
 * </p>
 */
public class BusinessException extends RuntimeException {

	private final HttpStatus status;
	private final String code;
	private final String field;

	public BusinessException(HttpStatus status, String code, String field, String message) {
		super(message);
		this.status = status;
		this.code = code;
		this.field = field;
	}

	public BusinessException(HttpStatus status, String code, String message) {
		this(status, code, null, message);
	}

	public HttpStatus status() {
		return status;
	}

	public String code() {
		return code;
	}

	public String field() {
		return field;
	}
}
