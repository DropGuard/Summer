package summer.web;

import java.util.Map;
import summer.validation.BodyValidator;
import summer.validation.ValidationResult;
import summer.web.exception.ArchitectureViolationException;
import summer.web.exception.BodyParseException;
import summer.web.exception.ValidationException;
import summer.web.exception.ValidatorNotFoundException;

/**
 * Facade for HTTP request processing. Controllers and framework handlers
 * interact with this — Response is fully encapsulated.
 */
public class WebContext {

	private final Request request;
	private final Response response = new Response();
	private final BodyValidator validator;
	private final BodyConverter jsonConverter;

	public WebContext(Request request) {
		this(request, null, new JsonBodyConverter());
	}

	public WebContext(Request request, BodyValidator validator, BodyConverter jsonConverter) {
		this.request = request;
		this.validator = validator;
		this.jsonConverter = jsonConverter;
	}

	// --- Read facade ---

	public Request request() {
		return request;
	}

	public HttpStatus statusCode() {
		return response.status;
	}

	public byte[] body() {
		return response.body;
	}

	public Map<String, String> headers() {
		return response.headers;
	}

	public Object resultObject() {
		return response.resultObject;
	}

	public BodyConverter converter() {
		return response.converter;
	}

	// --- Write facade ---

	public WebContext status(HttpStatus status) {
		response.status = status;
		return this;
	}

	public WebContext setHeader(String name, String value) {
		response.headers.put(name, value);
		return this;
	}

	public void json(HttpStatus status, Object data) {
		response.status = status;
		response.resultObject = data;
		response.converter = jsonConverter;
		response.headers.put("Content-Type", jsonConverter.getContentType());
	}

	public void ok(Object data) {
		json(HttpStatus.OK, data);
	}

	public void text(HttpStatus status, String body) {
		response.status = status;
		if (body != null) {
			response.body = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		}
		response.headers.put("Content-Type", "text/plain");
	}

	public void error(Throwable e) {
		String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
		text(HttpStatus.INTERNAL_SERVER_ERROR, message);
	}

	// --- Request helpers ---

	public String path() {
		return request.getPath();
	}

	public String method() {
		return request.getMethod();
	}

	public String header(String name) {
		return request.getHeader(name);
	}

	public String queryParam(String name) {
		return request.queryParam(name);
	}

	public String pathParam(String name) {
		return request.pathParam(name);
	}

	public <T> T body(Class<T> type) {
		return parseBody(type);
	}

	public <T> T validatedBody(Class<T> type) {
		T body = body(type);
		validate(body, type);
		return body;
	}

	private <T> T parseBody(Class<T> type) {
		if (!type.isRecord()) {
			throw new ArchitectureViolationException(type.getName());
		}
		try {
			return jsonConverter.read(request.getBody(), type);
		} catch (java.io.IOException e) {
			throw new BodyParseException(request.getContentType(), e);
		}
	}

	private <T> void validate(T body, Class<T> type) {
		if (validator == null) {
			throw new ValidatorNotFoundException();
		}
		if (body != null && validator.supports(type)) {
			ValidationResult result = validator.validate(body);
			if (!result.isValid()) {
				throw new ValidationException(result.getErrors());
			}
		}
	}
}
