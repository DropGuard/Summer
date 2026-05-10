package summer.web;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import summer.core.ApplicationContext;
import summer.validation.BodyValidator;
import summer.validation.ValidationResult;

/**
 * Represents an HTTP request.
 */
public class Request {
	private final String method;
	private final String path;
	private final String query;
	private final byte[] body;
	private final String contentType;
	private final Map<String, Object> attributes = new HashMap<>();

	public Request(String method, String path, String query, byte[] body) {
		this.method = method;
		this.path = path;
		this.query = query;
		this.body = body != null ? body : new byte[0];
		this.contentType = "application/json";
	}

	public Request(String method, String path, String query, String contentType, byte[] body) {
		this.method = method;
		this.path = path;
		this.query = query;
		this.body = body != null ? body : new byte[0];
		this.contentType = contentType;
	}

	public String getMethod() {
		return method;
	}

	public String getPath() {
		return path;
	}

	public String getQuery() {
		return query;
	}

	public byte[] getBody() {
		return body;
	}

	public String getContentType() {
		return contentType;
	}

	public boolean isGet() {
		return "GET".equals(method);
	}

	public boolean isPost() {
		return "POST".equals(method);
	}

	public boolean isPut() {
		return "PUT".equals(method);
	}

	public boolean isDelete() {
		return "DELETE".equals(method);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		Request request = (Request) o;
		return Objects.equals(method, request.method) && Objects.equals(path, request.path)
				&& Objects.equals(query, request.query);
	}

	@Override
	public int hashCode() {
		return Objects.hash(method, path, query);
	}

	@Override
	public String toString() {
		return "Request{" + "method='" + method + '\'' + ", path='" + path + '\'' + ", query='" + query + '\'' + '}';
	}

	public void setAttribute(String name, Object value) {
		attributes.put(name, value);
	}

	@SuppressWarnings("unchecked")
	public <T> T getAttribute(String name) {
		return (T) attributes.get(name);
	}

	public <T> T getAttribute(String name, Class<T> type) {
		Object value = attributes.get(name);
		if (value == null) {
			return null;
		}
		return type.cast(value);
	}

	public Map<String, Object> getAttributes() {
		return attributes;
	}

	// Query parameters parsing
	public Map<String, String> getQueryParameters() {
		Map<String, String> params = new HashMap<>();
		if (query != null && !query.isEmpty()) {
			String[] pairs = query.split("&");
			for (String pair : pairs) {
				int eqIndex = pair.indexOf('=');
				if (eqIndex != -1) {
					String name = pair.substring(0, eqIndex);
					String value = pair.substring(eqIndex + 1);
					try {
						params.put(name, java.net.URLDecoder.decode(value, "UTF-8"));
					} catch (Exception e) {
						params.put(name, value);
					}
				} else {
					params.put(pair, "");
				}
			}
		}
		return params;
	}

	public String getQueryParameter(String name) {
		return getQueryParameters().get(name);
	}

	// --- Explicit Parameter Extraction APIs ---

	/**
	 * Extracts a path parameter by name.
	 */
	public String pathParam(String name) {
		return getAttribute(name);
	}

	/**
	 * Extracts a query parameter by name.
	 */
	public String queryParam(String name) {
		return getQueryParameter(name);
	}

	/**
	 * Parses the JSON request body into the specified class type and validates it.
	 */
	public <T> T body(Class<T> type) {
		if (body == null || body.length == 0) {
			return null;
		}
		try {
			T obj = JsonConverter.fromJson(body, type);

			// Explicit Validation: Try to get BodyValidator and validate the object
			try {
				ApplicationContext context = ApplicationContext.getInstance();
				BodyValidator validator = context.getBean(BodyValidator.class);
				if (validator != null && validator.supports(type)) {
					ValidationResult result = validator.validate(obj);
					if (!result.isValid()) {
						throw new RuntimeException("Validation failed: " + String.join(", ", result.getErrors()));
					}
				}
			} catch (Exception e) {
				// If context not initialized or validator not found or not supporting this type,
				// skip validation. This keeps validation optional.
			}

			return obj;
		} catch (java.io.IOException e) {
			throw new RuntimeException("Failed to parse JSON body: " + e.getMessage(), e);
		}
	}
}
