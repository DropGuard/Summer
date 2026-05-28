package summer.core.exception;

import summer.core.ErrorCode;
import summer.core.SummerException;

/**
 * Base class for all data access and serialization exceptions.
 */
public class SummerDataException extends SummerException {
	public SummerDataException(ErrorCode errorCode, String message) {
		super(errorCode, message);
	}

	public SummerDataException(ErrorCode errorCode, String message, Throwable cause) {
		super(errorCode, message, cause);
	}
}
