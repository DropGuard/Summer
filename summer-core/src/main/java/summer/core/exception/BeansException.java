package summer.core.exception;

import summer.core.ErrorCode;
import summer.core.SummerException;

/**
 * Base class for all DI and component-related exceptions.
 */
public class BeansException extends SummerException {
	public BeansException(ErrorCode errorCode, String message) {
		super(errorCode, message);
	}

	public BeansException(ErrorCode errorCode, String message, Throwable cause) {
		super(errorCode, message, cause);
	}

	/**
	 * Backward-compatible constructor.
	 */
	public BeansException(String message) {
		super(ErrorCode.INTERNAL_ERROR, message);
	}

	/**
	 * Backward-compatible constructor.
	 */
	public BeansException(String message, Throwable cause) {
		super(ErrorCode.INTERNAL_ERROR, message, cause);
	}
}
