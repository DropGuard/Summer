package summer.core;

/**
 * Base exception class for all Summer framework exceptions.
 */
public class SummerException extends RuntimeException {
	private final ErrorCode errorCode;

	public SummerException(ErrorCode errorCode) {
		super(errorCode.defaultMessage());
		this.errorCode = errorCode;
	}

	public SummerException(ErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

	public SummerException(ErrorCode errorCode, String message, Throwable cause) {
		super(message, cause);
		this.errorCode = errorCode;
	}

	/**
	 * Backward-compatible constructor. Uses INTERNAL_ERROR as the default code.
	 */
	public SummerException(String message) {
		super(message);
		this.errorCode = ErrorCode.INTERNAL_ERROR;
	}

	/**
	 * Backward-compatible constructor. Uses INTERNAL_ERROR as the default code.
	 */
	public SummerException(String message, Throwable cause) {
		super(message, cause);
		this.errorCode = ErrorCode.INTERNAL_ERROR;
	}

	public ErrorCode errorCode() {
		return errorCode;
	}
}
