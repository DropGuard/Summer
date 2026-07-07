package summer.tx;

import summer.core.ErrorCode;
import summer.core.exception.SummerException;

/**
 * Exception class for transaction related errors in Summer framework.
 */
public class SummerTransactionException extends SummerException {

	public SummerTransactionException(String message) {
		super(ErrorCode.TRANSACTION_ERROR, message);
	}

	public SummerTransactionException(String message, Throwable cause) {
		super(ErrorCode.TRANSACTION_ERROR, message, cause);
	}
}
