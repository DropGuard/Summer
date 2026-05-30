package summer.web.exception;

import summer.core.ErrorCode;
import summer.core.SummerException;
import summer.web.HttpStatus;

/**
 * Base class for all Web and HTTP-related exceptions in Summer. Carries an HTTP
 * status code for automatic response mapping.
 */
public class SummerWebException extends SummerException {
	private final HttpStatus status;

	public SummerWebException(ErrorCode errorCode, HttpStatus status, String message) {
		super(errorCode, message);
		this.status = status;
	}

	public SummerWebException(ErrorCode errorCode, HttpStatus status, String message, Throwable cause) {
		super(errorCode, message, cause);
		this.status = status;
	}

	/**
	 * Backward-compatible constructor. Defaults to 500 status.
	 */
	public SummerWebException(String message) {
		super(ErrorCode.INTERNAL_ERROR, message);
		this.status = HttpStatus.INTERNAL_SERVER_ERROR;
	}

	/**
	 * Backward-compatible constructor. Defaults to 500 status.
	 */
	public SummerWebException(String message, Throwable cause) {
		super(ErrorCode.INTERNAL_ERROR, message, cause);
		this.status = HttpStatus.INTERNAL_SERVER_ERROR;
	}

	public HttpStatus statusCode() {
		return status;
	}
}
