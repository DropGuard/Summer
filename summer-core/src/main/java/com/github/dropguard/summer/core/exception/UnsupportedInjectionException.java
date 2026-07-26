package com.github.dropguard.summer.core.exception;

import com.github.dropguard.summer.core.ErrorCode;

/**
 * Thrown when attempting to inject a type that is currently not supported by
 * the framework, such as nested generic collections (e.g.
 * List<Strategy<String>>).
 */
public class UnsupportedInjectionException extends SummerException {
	public UnsupportedInjectionException(String message) {
		super(ErrorCode.UNSUPPORTED_INJECTION, message);
	}
}
