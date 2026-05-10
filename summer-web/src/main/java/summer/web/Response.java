package summer.web;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import summer.validation.ValidationResult;

/**
 * Represents an HTTP response with streaming capabilities.
 */
public class Response {
	private final OutputStream output;
	private final Map<String, String> headers = new HashMap<>();
	private int statusCode = 200;
	private boolean committed = false;

	public Response(OutputStream output) {
		this.output = output;
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

	public void ok(String content) {
		send(200, content.getBytes(StandardCharsets.UTF_8), "text/plain");
	}

	public void ok(Object content) {
		sendJson(200, content);
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

	public void json(Object content) {
		sendJson(200, content);
	}

	public void send(int statusCode, byte[] body, String contentType) {
		if (committed) return;
		this.statusCode = statusCode;

		try {
			if (contentType != null) {
				setHeader("Content-Type", contentType);
			}
			if (body != null) {
				setHeader("Content-Length", String.valueOf(body.length));
			}

			// Write Status Line
			String statusLine = "HTTP/1.1 " + statusCode + " " + getStatusCodeText(statusCode) + "\r\n";
			output.write(statusLine.getBytes(StandardCharsets.UTF_8));

			// Write Headers
			for (Map.Entry<String, String> entry : headers.entrySet()) {
				String headerLine = entry.getKey() + ": " + entry.getValue() + "\r\n";
				output.write(headerLine.getBytes(StandardCharsets.UTF_8));
			}

			// End of Headers
			output.write("\r\n".getBytes(StandardCharsets.UTF_8));

			// Write Body
			if (body != null && body.length > 0) {
				output.write(body);
			}
			output.flush();
			committed = true;
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void sendJson(int statusCode, Object content) {
		try {
			String json = JsonConverter.toJson(content);
			send(statusCode, json.getBytes(StandardCharsets.UTF_8), "application/json");
		} catch (Exception e) {
			error("JSON Serialization Error: " + e.getMessage());
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

	public boolean isCommitted() {
		return committed;
	}
}
