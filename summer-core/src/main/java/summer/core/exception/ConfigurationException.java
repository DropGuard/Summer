package summer.core.exception;

import summer.core.ErrorCode;
import summer.core.SummerException;

/**
 * Thrown when there is an issue loading or parsing the application
 * configuration.
 */
public class ConfigurationException extends SummerException {
	public ConfigurationException(ErrorCode errorCode, String message) {
		super(errorCode, message);
	}

	public ConfigurationException(ErrorCode errorCode, String message, Throwable cause) {
		super(errorCode, message, cause);
	}

	/**
	 * Backward-compatible constructor.
	 */
	public ConfigurationException(String message) {
		super(ErrorCode.CONFIG_PARSE_ERROR, message);
	}

	/**
	 * Backward-compatible constructor.
	 */
	public ConfigurationException(String message, Throwable cause) {
		super(ErrorCode.CONFIG_PARSE_ERROR, message, cause);
	}
}
