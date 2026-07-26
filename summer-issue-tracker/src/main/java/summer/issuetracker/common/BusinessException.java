package summer.issuetracker.common;

import summer.web.HttpStatus;

/**
 * Base for all demo-domain errors. Carries an HTTP status so the global error
 * handler can translate it to a response without leaking exception types.
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public BusinessException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public BusinessException(HttpStatus status, String message) {
        this(status, "BUSINESS_ERROR", message);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public static BusinessException notFound(String what) {
        return new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", what + " not found");
    }

    public static BusinessException forbidden(String reason) {
        return new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", reason);
    }

    public static BusinessException badRequest(String reason) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", reason);
    }

    public static BusinessException unauthorized(String reason) {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", reason);
    }

    public static BusinessException conflict(String reason) {
        return new BusinessException(HttpStatus.CONFLICT, "CONFLICT", reason);
    }
}
