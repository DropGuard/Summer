package summer.core.exception;

import summer.core.ErrorCode;

/**
 * Thrown when multiple beans match a required dependency type, causing
 * ambiguity.
 */
public class AmbiguousBeanException extends SummerException {

	public AmbiguousBeanException(String message) {
		super(ErrorCode.AMBIGUOUS_BEAN, message);
	}
}
