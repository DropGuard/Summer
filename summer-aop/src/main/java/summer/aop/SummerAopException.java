package summer.aop;

/**
 * Exception class for AOP related errors in Summer framework.
 */
public class SummerAopException extends RuntimeException {
	public SummerAopException(String message) {
		super(message);
	}

	public SummerAopException(String message, Throwable cause) {
		super(message, cause);
	}
}