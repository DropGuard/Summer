package summer.web.exception;

import summer.core.ErrorCode;
import summer.core.SummerException;

/**
 * Base class for all Web and HTTP-related exceptions in Summer. Carries an HTTP
 * status code for automatic response mapping.
 */
public class SummerWebException extends SummerException {
	private final int statusCode;

	public SummerWebException(ErrorCode errorCode, int statusCode, String message) {
		super(errorCode, message);
		this.statusCode = statusCode;
	}

	public SummerWebException(ErrorCode errorCode, int statusCode, String message, Throwable cause) {
		super(errorCode, message, cause);
		this.statusCode = statusCode;
	}

	/**
	 * Backward-compatible constructor. Defaults to 500 status.
	 */
	public SummerWebException(String message) {
		super(ErrorCode.INTERNAL_ERROR, message);
		this.statusCode = 500;
	}

	/**
	 * Backward-compatible constructor. Defaults to 500 status.
	 */
	public SummerWebException(String message, Throwable cause) {
		super(ErrorCode.INTERNAL_ERROR, message, cause);
		this.statusCode = 500;
	}

	public int statusCode() {
		return statusCode;
	}
}
