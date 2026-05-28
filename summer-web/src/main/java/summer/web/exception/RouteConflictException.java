package summer.web.exception;

import summer.core.ErrorCode;

/**
 * Thrown when route registration conflicts with an existing route.
 */
public class RouteConflictException extends SummerWebException {
	public RouteConflictException(String path) {
		super(ErrorCode.ROUTE_CONFLICT, 500, "Route conflict: parameter name mismatch at " + path);
	}
}
