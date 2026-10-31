package com.github.dropguard.summer.core.exception;

import com.github.dropguard.summer.core.ErrorCode;

/**
 * Base exception for framework-assembly and infrastructure failures — bean creation, discovery,
 * configuration, data access. Carries a machine-readable {@link ErrorCode} for tooling and engine
 * branching.
 *
 * <p>Deliberately NOT the root of the request-scoped family: {@code
 * com.github.dropguard.summer.web.exception.HttpException} extends {@code RuntimeException}
 * directly because an HTTP response already carries its own machine-readable identifier — the
 * status code. Two roots, two identifier systems: {@code ErrorCode} for assembly failures, HTTP
 * status for request failures.
 */
public class SummerException extends RuntimeException {
    private final ErrorCode errorCode;

    public SummerException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SummerException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
