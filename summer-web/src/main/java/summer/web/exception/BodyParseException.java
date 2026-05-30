package summer.web.exception;

import summer.core.ErrorCode;
import summer.web.HttpStatus;

/**
 * Thrown when request body parsing fails.
 */
public class BodyParseException extends SummerWebException {
	public BodyParseException(String converterName, Throwable cause) {
		super(ErrorCode.BODY_PARSE_ERROR, HttpStatus.BAD_REQUEST, "Failed to parse body with " + converterName, cause);
	}
}
