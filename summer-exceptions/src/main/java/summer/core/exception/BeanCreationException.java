package summer.core.exception;

import summer.core.ErrorCode;

/**
 * Thrown when a bean fails to be instantiated or its @Bean method fails to
 * invoke.
 */
public class BeanCreationException extends SummerException {
	public BeanCreationException(ErrorCode errorCode, String message) {
		super(errorCode, message);
	}

	public BeanCreationException(ErrorCode errorCode, String message, Throwable cause) {
		super(errorCode, message, cause);
	}

	/**
	 * Backward-compatible constructor.
	 */
	public BeanCreationException(String message) {
		super(ErrorCode.BEAN_CREATION_FAILED, message);
	}

	/**
	 * Backward-compatible constructor.
	 */
	public BeanCreationException(String message, Throwable cause) {
		super(ErrorCode.BEAN_CREATION_FAILED, message, cause);
	}
}
