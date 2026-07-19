package summer.twitter.common;

import summer.web.HttpStatus;

/**
 * Thrown when an operation is rejected because of its arguments or business
 * rules (e.g. a user trying to follow themselves). Maps to HTTP 400.
 */
public class IllegalOperationException extends BusinessException {

	public IllegalOperationException(String message) {
		super(HttpStatus.BAD_REQUEST, "illegal_operation", message);
	}
}
