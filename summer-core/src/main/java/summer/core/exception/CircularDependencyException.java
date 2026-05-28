package summer.core.exception;

import summer.core.ErrorCode;

/**
 * Thrown when a circular dependency is detected between beans.
 */
public class CircularDependencyException extends BeansException {
	public CircularDependencyException(ErrorCode errorCode, String message) {
		super(errorCode, message);
	}

	/**
	 * Backward-compatible constructor.
	 */
	public CircularDependencyException(String message) {
		super(ErrorCode.CIRCULAR_DEPENDENCY, message);
	}
}
