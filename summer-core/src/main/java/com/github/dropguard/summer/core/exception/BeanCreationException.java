package com.github.dropguard.summer.core.exception;

import com.github.dropguard.summer.core.ErrorCode;

/**
 * Thrown when a bean fails to be instantiated or its @Bean method fails to
 * invoke.
 */
public class BeanCreationException extends SummerException {

	public BeanCreationException(String message) {
		super(ErrorCode.BEAN_CREATION_FAILED, message);
	}

	public BeanCreationException(String message, Throwable cause) {
		super(ErrorCode.BEAN_CREATION_FAILED, message, cause);
	}
}
