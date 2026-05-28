package summer.core.exception;

import summer.core.ErrorCode;

/**
 * Thrown when multiple beans match a required dependency type, causing
 * ambiguity.
 */
public class AmbiguousBeanException extends BeansException {
	public AmbiguousBeanException(ErrorCode errorCode, String message) {
		super(errorCode, message);
	}

	/**
	 * Backward-compatible constructor.
	 */
	public AmbiguousBeanException(String message) {
		super(ErrorCode.AMBIGUOUS_BEAN, message);
	}
}
