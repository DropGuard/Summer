package summer.core.exception;

import summer.core.ErrorCode;

/**
 * Thrown when multiple {@code @Replaces} annotations target the same
 * configuration class.
 */
public class DuplicateReplacementException extends SummerException {
	public DuplicateReplacementException(Class<?> first, Class<?> second, Class<?> target) {
		super(ErrorCode.INTERNAL_ERROR, "Duplicate @Replaces: both " + first.getName() + " and " + second.getName()
				+ " replace " + target.getName());
	}
}
