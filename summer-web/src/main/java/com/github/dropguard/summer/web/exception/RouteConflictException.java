package com.github.dropguard.summer.web.exception;

import com.github.dropguard.summer.core.ErrorCode;
import com.github.dropguard.summer.web.HttpStatus;

/** Thrown when route registration conflicts with an existing route. */
public class RouteConflictException extends SummerWebException {
    /**
     * Two different parameter names claim the same position (e.g. {@code /{id}} vs {@code
     * /{name}}).
     */
    public RouteConflictException(String path) {
        super(
                ErrorCode.ROUTE_CONFLICT,
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Route conflict: parameter name mismatch at " + path);
    }

    /**
     * The exact same METHOD+path pattern was registered twice. Registration order must never decide
     * which handler wins — the second registration is rejected instead.
     */
    public static RouteConflictException duplicate(String methodAndPath) {
        return new RouteConflictException(
                ErrorCode.ROUTE_CONFLICT,
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Duplicate route registration: "
                        + methodAndPath
                        + " is already registered; later registration would silently win");
    }

    private RouteConflictException(ErrorCode errorCode, HttpStatus status, String message) {
        super(errorCode, status, message);
    }
}
