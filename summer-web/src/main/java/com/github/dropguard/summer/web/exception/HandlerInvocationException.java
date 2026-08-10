package com.github.dropguard.summer.web.exception;

import com.github.dropguard.summer.core.ErrorCode;
import com.github.dropguard.summer.core.exception.SummerException;

/**
 * Thrown when a request handler method cannot be invoked: it threw a checked exception (which the
 * {@code Handler.handle} contract cannot propagate) or reflection could not access it. A
 * framework-infrastructure failure — deliberately NOT a {@link SummerWebException}, whose message
 * is sent to the client; this one must stay server-side (the server responds with a generic 500).
 */
public class HandlerInvocationException extends SummerException {

    public HandlerInvocationException(String message, Throwable cause) {
        super(ErrorCode.HANDLER_INVOCATION_FAILED, message, cause);
    }

    public HandlerInvocationException(String message) {
        super(ErrorCode.HANDLER_INVOCATION_FAILED, message);
    }
}
