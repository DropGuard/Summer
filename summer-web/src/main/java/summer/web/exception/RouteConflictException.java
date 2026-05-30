package summer.web.exception;

import summer.core.ErrorCode;
import summer.web.HttpStatus;

/**
 * Thrown when route registration conflicts with an existing route.
 */
public class RouteConflictException extends SummerWebException {
	public RouteConflictException(String path) {
		super(ErrorCode.ROUTE_CONFLICT, HttpStatus.INTERNAL_SERVER_ERROR,
				"Route conflict: parameter name mismatch at " + path);
	}
}
