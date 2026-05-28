package summer.web;

import java.util.List;
import summer.core.ErrorCode;
import summer.validation.BodyValidator;
import summer.validation.ValidationResult;
import summer.web.exception.SummerWebException;
import summer.web.exception.ValidationException;

/**
 * Encapsulates the HTTP request and response for a single web exchange.
 */
public class WebContext {
	private final Request request;
	private final Response response;
	private final BodyValidator validator;
	private final List<BodyConverter> converters;

	public WebContext(Request request, Response response) {
		this(request, response, null, List.of());
	}

	public WebContext(Request request, Response response, BodyValidator validator, List<BodyConverter> converters) {
		this.request = request;
		this.response = response;
		this.validator = validator;
		this.converters = converters;
	}

	public Request request() {
		return request;
	}

	public Response response() {
		return response;
	}

	/**
	 * Parses the body using a matching converter and performs validation if a
	 * validator is present. Enforces that the target type must be a Java Record.
	 */
	public <T> T body(Class<T> type) {
		return parseBody(type, false);
	}

	/**
	 * Parses the body and strictly requires a Validator to be present.
	 */
	public <T> T validatedBody(Class<T> type) {
		return parseBody(type, true);
	}

	private <T> T parseBody(Class<T> type, boolean requireValidation) {
		if (!type.isRecord()) {
			throw new SummerWebException(ErrorCode.ARCHITECTURE_VIOLATION, 400,
					String.format("Architecture Violation: Class [%s] is not a Record. "
							+ "Summer enforces immutable Records for Request DTOs to ensure thread-safety, clarity, and future-proof performance. "
							+ "Please use 'record' instead of 'class' for your data transfer objects.",
							type.getName()));
		}

		String contentType = request.getContentType();
		BodyConverter converter = findConverter(contentType);

		try {
			T body = converter.read(request.getBody(), type);

			if (requireValidation) {
				if (validator == null) {
					throw new SummerWebException(ErrorCode.VALIDATION_FAILED, 500,
							"Validation is required for parameter annotated with @Valid, but no BodyValidator component was found. Did you forget to import a validation module (e.g. summer-validation-hv)?");
				}
				if (body != null && validator.supports(type)) {
					ValidationResult result = validator.validate(body);
					if (!result.isValid()) {
						throw new ValidationException(result.getErrors());
					}
				}
			}

			return body;
		} catch (java.io.IOException e) {
			throw new summer.web.exception.BodyParseException(converter.getClass().getSimpleName(), e);
		}
	}

	private static final BodyConverter DEFAULT_JSON_CONVERTER = new JsonBodyConverter();

	public BodyConverter findConverter(String contentType) {
		if (converters != null) {
			for (BodyConverter converter : converters) {
				if (converter.supports(contentType)) {
					return converter;
				}
			}
		}
		return DEFAULT_JSON_CONVERTER;
	}

	public List<BodyConverter> converters() {
		return converters;
	}

	// --- Request Facade Methods ---

	/**
	 * Retrieves a path parameter by name.
	 */
	public String pathParam(String name) {
		return request.pathParam(name);
	}

	/**
	 * Retrieves a query parameter by name.
	 */
	public String queryParam(String name) {
		return request.queryParam(name);
	}

	/**
	 * Retrieves a request header by name.
	 */
	public String header(String name) {
		return request.getHeader(name);
	}

	// --- Response Facade Methods ---

	/**
	 * Sets the response status code. Returns this context for chaining.
	 */
	public WebContext status(int statusCode) {
		response.setStatusCode(statusCode);
		return this;
	}

	/**
	 * Sets a response header. Returns this context for chaining.
	 */
	public WebContext setHeader(String name, String value) {
		response.setHeader(name, value);
		return this;
	}

	// Shortcut methods for convenience
	public String path() {
		return request.getPath();
	}

	public String method() {
		return request.getMethod();
	}

	public void send(int statusCode, Object result) {
		String acceptHeader = request.getHeader("Accept");
		BodyConverter converter = findConverter(acceptHeader);
		response.setStatusCode(statusCode);
		response.setResultObject(result);
		response.setConverter(converter);
		if (converter.getContentType() != null) {
			response.setHeader("Content-Type", converter.getContentType());
		}
		response.setCommitted(true);
	}

	public void ok(Object result) {
		send(200, result);
	}

	public void notFound() {
		response.notFound();
	}

	public void error(Exception e) {
		response.error(e);
	}
}
