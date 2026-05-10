package summer.web;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import summer.validation.ValidationResult;

/**
 * Represents an HTTP response.
 */
public class Response {
	private final OutputStream output;
	private final Map<String, String> headers = new HashMap<>();
	private int statusCode = 200;

	public Response(OutputStream output) {
		this.output = output;
	}

	public OutputStream getOutputStream() {
		return output;
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

	public String getHeader(String name) {
		return headers.get(name);
	}

	public void ok(String content) {
		send(200, content, "text/plain");
	}

	public void ok(Object content) {
		sendJson(200, content);
	}

	public void created(String location) {
		setHeader("Location", location);
		send(201, null, null);
	}

	public void created(String location, Object content) {
		setHeader("Location", location);
		sendJson(201, content);
	}

	public void notFound() {
		send(404, "Not Found", "text/plain");
	}

	public void badRequest(String message) {
		send(400, message, "text/plain");
	}

	public void validationError(ValidationResult validationResult) {
		// Create a JSON response with validation errors
		Map<String, Object> errorResponse = new HashMap<>();
		errorResponse.put("error", "Validation Failed");
		errorResponse.put("status", 400);
		errorResponse.put("details", validationResult.getErrors());

		try {
			String json = JsonConverter.toJson(errorResponse);
			send(400, json, "application/json");
		} catch (Exception e) {
			send(400, "Validation failed", "text/plain");
		}
	}

	public void error(String message) {
		send(500, message, "text/plain");
	}

	public void error(Exception e) {
		send(500, e.getMessage(), "text/plain");
	}

	public void json(Object content) {
		sendJson(200, content);
	}

	public void send(int statusCode, String content, String contentType) {
		this.statusCode = statusCode;

		try {
			if (contentType != null) {
				setHeader("Content-Type", contentType);
			}

			String statusLine = "HTTP/1.1 " + statusCode + " " + getStatusCodeText(statusCode);
			StringBuilder headerLines = new StringBuilder();

			for (Map.Entry<String, String> entry : headers.entrySet()) {
				headerLines.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
			}

			String response = statusLine + "\r\n" + headerLines.toString() + "\r\n" + (content != null ? content : "");

			output.write(response.getBytes(StandardCharsets.UTF_8));
			output.flush();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void sendJson(int statusCode, Object content) {
		try {
			String json = JsonConverter.toJson(content);
			send(statusCode, json, "application/json");
		} catch (Exception e) {
			send(500, "Error converting to JSON: " + e.getMessage(), "text/plain");
		}
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
}