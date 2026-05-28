package summer.tx;

import summer.core.ErrorCode;
import summer.core.SummerException;

/**
 * Exception class for transaction related errors in Summer framework.
 */
public class SummerTransactionException extends SummerException {
	public SummerTransactionException(ErrorCode errorCode, String message) {
		super(errorCode, message);
	}

	public SummerTransactionException(ErrorCode errorCode, String message, Throwable cause) {
		super(errorCode, message, cause);
	}

	/**
	 * Backward-compatible constructor.
	 */
	public SummerTransactionException(String message) {
		super(ErrorCode.TRANSACTION_ERROR, message);
	}

	/**
	 * Backward-compatible constructor.
	 */
	public SummerTransactionException(String message, Throwable cause) {
		super(ErrorCode.TRANSACTION_ERROR, message, cause);
	}
}
