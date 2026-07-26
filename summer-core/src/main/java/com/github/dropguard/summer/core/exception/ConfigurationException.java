package com.github.dropguard.summer.core.exception;

import com.github.dropguard.summer.core.ErrorCode;

/**
 * Thrown when there is an issue loading or parsing the application
 * configuration.
 */
public class ConfigurationException extends SummerException {
	public ConfigurationException(ErrorCode errorCode, String message) {
		super(errorCode, message);
	}

	public ConfigurationException(ErrorCode errorCode, String message, Throwable cause) {
		super(errorCode, message, cause);
	}
}
