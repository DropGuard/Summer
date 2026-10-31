package com.github.dropguard.summer.web.exception;

import com.github.dropguard.summer.core.ErrorCode;
import com.github.dropguard.summer.core.exception.SummerException;

/**
 * Thrown when route registration conflicts with an existing route. Thrown exclusively during router
 * BUILD — the routers are immutable afterwards, so it can never surface as an HTTP response — and
 * therefore belongs to the assembly-failure tree, not to {@code HttpException}.
 */
public class RouteConflictException extends SummerException {

    /**
     * Two different parameter names claim the same position (e.g. {@code /{id}} vs {@code
     * /{name}}).
     */
    public RouteConflictException(String path) {
        super(ErrorCode.ROUTE_CONFLICT, "Route conflict: parameter name mismatch at " + path);
    }

    /**
     * The exact same METHOD+path pattern was registered twice. Registration order must never decide
     * which handler wins — the second registration is rejected instead.
     */
    public static RouteConflictException duplicate(String methodAndPath) {
        return new RouteConflictException("Duplicate route registration: " + methodAndPath);
    }
}
