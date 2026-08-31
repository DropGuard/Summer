package com.github.dropguard.summer.web.exception;

/**
 * Base exception for HTTP-related errors that carry an HTTP status code.
 *
 * <p>Subclasses should define specific error semantics and may carry additional domain-specific
 * information for error response building.
 */
public abstract class HttpException extends RuntimeException {

    private final int status;

    protected HttpException(int status, String message) {
        super(message);
        this.status = status;
    }

    /**
     * @return the HTTP status code associated with this exception
     */
    public int getStatus() {
        return status;
    }
}
