package com.github.dropguard.summer.web.exception;

import com.github.dropguard.summer.web.HttpStatus;

/** Thrown when the user has sent too many requests in a given amount of time ("rate limiting"). */
public class TooManyRequestsException extends HttpException {
    public TooManyRequestsException(String message) {

        super(HttpStatus.TOO_MANY_REQUESTS.code(), message);
    }

    /** Convenience method */
    public static TooManyRequestsException tooManyRequests(String message) {
        return new TooManyRequestsException(message);
    }
}
