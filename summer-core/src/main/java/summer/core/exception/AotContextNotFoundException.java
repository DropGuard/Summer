package summer.core.exception;

import summer.core.ErrorCode;

/**
 * Thrown when AOT context is requested but not found on the classpath.
 */
public class AotContextNotFoundException extends SummerException {
	public AotContextNotFoundException() {
		super(ErrorCode.INTERNAL_ERROR,
				"AOT Context not found. Ensure summer-maven-plugin is configured and ran during build.");
	}
}
