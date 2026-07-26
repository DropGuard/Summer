package com.github.dropguard.summer.core.exception;

import com.github.dropguard.summer.core.ErrorCode;

/**
 * Thrown when multiple beans match a required dependency type, causing
 * ambiguity.
 */
public class AmbiguousBeanException extends SummerException {

	public AmbiguousBeanException(String message) {
		super(ErrorCode.AMBIGUOUS_BEAN, message);
	}
}
