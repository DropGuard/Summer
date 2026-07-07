package summer.core.exception;

import summer.core.ErrorCode;

/**
 * Thrown when a circular dependency is detected between beans.
 */
public class CircularDependencyException extends SummerException {

	public CircularDependencyException(String message) {
		super(ErrorCode.CIRCULAR_DEPENDENCY, message);
	}
}
