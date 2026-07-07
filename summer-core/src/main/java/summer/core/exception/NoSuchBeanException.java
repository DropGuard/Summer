package summer.core.exception;

import summer.core.ErrorCode;

/**
 * Thrown when a required bean cannot be found in the container.
 */
public class NoSuchBeanException extends SummerException {

	public NoSuchBeanException(String message) {
		super(ErrorCode.BEAN_NOT_FOUND, message);
	}
}
