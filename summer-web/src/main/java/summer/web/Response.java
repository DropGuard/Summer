package summer.web;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents an HTTP response with streaming capabilities.
 */
public class Response {
	private final Map<String, String> headers = new HashMap<>();
	private int statusCode = 200;
	private boolean committed = false;
	private byte[] bodyBytes;
	private Object resultObject;
	private summer.web.BodyConverter converter;

	public Response() {
	}

	public void setStatusCode(int statusCode) {
		this.statusCode = statusCode;
	}

	public int getStatusCode() {
		return statusCode;
	}

	public void setHeader(String name, String value) {
		headers.put(name, value);
	}

	public Map<String, String> getHeaders() {
		return headers;
	}

	public void ok(String content) {
		send(200, content.getBytes(StandardCharsets.UTF_8), "text/plain");
	}

	public void created(String location) {
		setHeader("Location", location);
		send(201, new byte[0], null);
	}

	public void notFound() {
		send(404, "Not Found".getBytes(StandardCharsets.UTF_8), "text/plain");
	}

	public void badRequest(String message) {
		send(400, message.getBytes(StandardCharsets.UTF_8), "text/plain");
	}

	public void error(String message) {
		send(500, message.getBytes(StandardCharsets.UTF_8), "text/plain");
	}

	public void error(Exception e) {
		error(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
	}

	public void send(int statusCode, byte[] body, String contentType) {
		if (committed)
			return;
		this.statusCode = statusCode;
		this.bodyBytes = body;
		if (contentType != null) {
			setHeader("Content-Type", contentType);
		}
		if (body != null) {
			setHeader("Content-Length", String.valueOf(body.length));
		}
		committed = true;
	}

	public byte[] getBody() {
		return bodyBytes;
	}

	private String getStatusCodeText(int statusCode) {
		return switch (statusCode) {
			case 200 -> "OK";
			case 201 -> "Created";
			case 400 -> "Bad Request";
			case 404 -> "Not Found";
			case 500 -> "Internal Server Error";
			default -> "Unknown";
		};
	}

	public boolean isCommitted() {
		return committed;
	}

	public void setResultObject(Object resultObject) {
		this.resultObject = resultObject;
	}

	public Object getResultObject() {
		return resultObject;
	}

	public void setConverter(summer.web.BodyConverter converter) {
		this.converter = converter;
	}

	public summer.web.BodyConverter getConverter() {
		return converter;
	}

	public void setCommitted(boolean committed) {
		this.committed = committed;
	}
}
