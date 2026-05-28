package summer.core.exception;

import summer.core.ErrorCode;
import summer.core.SummerException;

/**
 * Thrown when AOT context is requested but not found on the classpath.
 */
public class AotContextNotFoundException extends SummerException {
	public AotContextNotFoundException() {
		super(ErrorCode.INTERNAL_ERROR,
				"AOT Context not found. Ensure summer-compiler ran and ServiceLoader configuration exists.");
	}
}
