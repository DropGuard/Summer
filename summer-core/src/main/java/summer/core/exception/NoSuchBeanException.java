package summer.core.exception;

import summer.core.ErrorCode;

/**
 * Thrown when a required bean could not be found in the application context.
 */
public class NoSuchBeanException extends SummerException {
	public NoSuchBeanException(ErrorCode errorCode, String message) {
		super(errorCode, message);
	}

	/**
	 * Backward-compatible constructor.
	 */
	public NoSuchBeanException(String message) {
		super(ErrorCode.BEAN_NOT_FOUND, message);
	}
}
