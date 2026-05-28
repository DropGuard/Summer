package summer.core.exception;

import summer.core.ErrorCode;

/**
 * Thrown when a database access operation fails.
 */
public class DataAccessException extends SummerDataException {
	public DataAccessException(String message) {
		super(ErrorCode.DATA_ACCESS_ERROR, message);
	}

	public DataAccessException(String message, Throwable cause) {
		super(ErrorCode.DATA_ACCESS_ERROR, message, cause);
	}
}
