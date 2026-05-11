package summer.web;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents an HTTP request.
 */
public class Request {
	private final String method;
	private final String path;
	private final byte[] rawPathBytes;
	private final String query;
	private final byte[] body;
	private final String contentType;
	private final Map<String, String> headers;
	private final Map<String, Object> attributes = new HashMap<>();

	public Request(String method, String path, String query, String contentType, byte[] body) {
		this(method, path, query, contentType, body, new HashMap<>(), path.getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}

	public Request(String method, String path, String query, String contentType, byte[] body, Map<String, String> headers, byte[] rawPathBytes) {
		this.method = method;
		this.path = path;
		this.rawPathBytes = rawPathBytes;
		this.query = query;
		this.body = body != null ? body : new byte[0];
		this.contentType = contentType;
		this.headers = headers != null ? headers : new HashMap<>();
	}

	public String getMethod() {
		return method;
	}

	public String getPath() {
		return path;
	}

	public byte[] getRawPathBytes() {
		return rawPathBytes;
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

	public String getHeader(String name) {
		return headers.get(name.toLowerCase());
	}

	public Map<String, String> getHeaders() {
		return headers;
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
}
