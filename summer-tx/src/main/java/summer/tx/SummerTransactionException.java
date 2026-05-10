package summer.tx;

/**
 * Exception class for transaction related errors in Summer framework.
 */
public class SummerTransactionException extends RuntimeException {
	public SummerTransactionException(String message) {
		super(message);
	}

	public SummerTransactionException(String message, Throwable cause) {
		super(message, cause);
	}
}