package summer.web;

/**
 * Standard HTTP status codes.
 */
public enum HttpStatus {

	// 1xx Informational
	CONTINUE(100, "Continue"), SWITCHING_PROTOCOLS(101, "Switching Protocols"),

	// 2xx Success
	OK(200, "OK"), CREATED(201, "Created"), ACCEPTED(202, "Accepted"), NO_CONTENT(204, "No Content"),

	// 3xx Redirection
	MOVED_PERMANENTLY(301, "Moved Permanently"), FOUND(302, "Found"), NOT_MODIFIED(304, "Not Modified"),

	// 4xx Client Error
	BAD_REQUEST(400, "Bad Request"), UNAUTHORIZED(401, "Unauthorized"), FORBIDDEN(403, "Forbidden"), NOT_FOUND(404,
			"Not Found"), METHOD_NOT_ALLOWED(405, "Method Not Allowed"), CONFLICT(409,
					"Conflict"), UNPROCESSABLE_ENTITY(422,
							"Unprocessable Entity"), TOO_MANY_REQUESTS(429, "Too Many Requests"),

	// 5xx Server Error
	INTERNAL_SERVER_ERROR(500, "Internal Server Error"), NOT_IMPLEMENTED(501, "Not Implemented"), BAD_GATEWAY(502,
			"Bad Gateway"), SERVICE_UNAVAILABLE(503, "Service Unavailable"), GATEWAY_TIMEOUT(504, "Gateway Timeout");

	private final int code;
	private final String reason;

	HttpStatus(int code, String reason) {
		this.code = code;
		this.reason = reason;
	}

	public int code() {
		return code;
	}

	public String reason() {
		return reason;
	}

	/**
	 * Resolve an HttpStatus from a numeric code. Returns null for unknown codes.
	 */
	public static HttpStatus fromCode(int code) {
		for (HttpStatus status : values()) {
			if (status.code == code) {
				return status;
			}
		}
		return null;
	}
}
