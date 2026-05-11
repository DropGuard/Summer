package summer.web.server;

import java.io.InputStream;
import summer.web.Request;

/**
 * Parses raw InputStream bytes from a TCP socket into a structured HTTP
 * Request.
 */
public class HttpRequestParser {

	public static Request parse(InputStream input, int maxBodySize, int readTimeout) throws java.io.IOException {
		long startTime = System.currentTimeMillis();
		
		// 1. Read the Request-Line and Headers as bytes until we find the double CRLF
		// Using a local buffer for low-level byte scanning
		byte[] buffer = new byte[16384]; // 16KB limit
		int bufferLen = 0;
		int lastFour = 0;
		int b;
		while ((b = input.read()) != -1) {
			buffer[bufferLen++] = (byte) b;
			lastFour = (lastFour << 8) | (b & 0xFF);
			if (lastFour == 0x0D0A0D0A) { // \r\n\r\n
				break;
			}
			if (bufferLen >= buffer.length) {
				throw new RuntimeException("Header size too large");
			}
			if (System.currentTimeMillis() - startTime > readTimeout) {
				throw new java.net.SocketTimeoutException("Request header read timeout");
			}
		}

		if (bufferLen == 0) return null;

		// 2. Fast byte-level scanning of the Request-Line (first line)
		// Format: METHOD PATH VERSION\r\n
		int methodEnd = -1;
		int pathStart = -1;
		int pathEnd = -1;
		int firstLineEnd = -1;

		for (int i = 0; i < bufferLen; i++) {
			if (buffer[i] == ' ' && methodEnd == -1) {
				methodEnd = i;
				pathStart = i + 1;
			} else if (buffer[i] == ' ' && pathEnd == -1) {
				pathEnd = i;
			} else if (buffer[i] == '\r' && i + 1 < bufferLen && buffer[i+1] == '\n') {
				firstLineEnd = i;
				break;
			}
		}

		if (methodEnd == -1 || pathStart == -1 || pathEnd == -1) return null;

		String method = new String(buffer, 0, methodEnd, java.nio.charset.StandardCharsets.UTF_8);
		
		// Capture Path Bytes directly
		int rawPathLen = pathEnd - pathStart;
		byte[] rawPathBytes = new byte[rawPathLen];
		System.arraycopy(buffer, pathStart, rawPathBytes, 0, rawPathLen);
		
		String rawPath = new String(rawPathBytes, java.nio.charset.StandardCharsets.UTF_8);
		String path = rawPath;
		String query = "";
		int queryIndex = rawPath.indexOf('?');
		if (queryIndex != -1) {
			path = rawPath.substring(0, queryIndex);
			query = rawPath.substring(queryIndex + 1);
			// Update rawPathBytes to exclude query for the router if needed, 
			// but usually the router wants the full path. Let's keep it simple.
			byte[] pathOnlyBytes = new byte[queryIndex];
			System.arraycopy(rawPathBytes, 0, pathOnlyBytes, 0, queryIndex);
			rawPathBytes = pathOnlyBytes;
		}

		// 3. Parse Headers from the remaining buffer
		java.util.Map<String, String> headers = new java.util.HashMap<>();
		int contentLength = 0;
		String contentType = "application/json";

		String headerPart = new String(buffer, firstLineEnd + 2, bufferLen - (firstLineEnd + 2), java.nio.charset.StandardCharsets.UTF_8);
		String[] lines = headerPart.split("\r\n");

		for (String line : lines) {
			if (line.isEmpty()) break;
			int colonIndex = line.indexOf(':');
			if (colonIndex != -1) {
				String name = line.substring(0, colonIndex).trim();
				String value = line.substring(colonIndex + 1).trim();
				headers.put(name.toLowerCase(), value);

				if (name.equalsIgnoreCase("Content-Length")) {
					contentLength = Integer.parseInt(value);
					if (contentLength > maxBodySize) {
						throw new RuntimeException("Payload Too Large: " + contentLength + " > " + maxBodySize);
					}
				} else if (name.equalsIgnoreCase("Content-Type")) {
					contentType = value;
				} else if (name.equalsIgnoreCase("Transfer-Encoding") && value.toLowerCase().contains("chunked")) {
					throw new RuntimeException("Chunked Transfer-Encoding not supported. Use a reverse proxy.");
				}
			}
		}

		// 4. Read Body
		byte[] body = new byte[0];
		if (contentLength > 0) {
			body = new byte[contentLength];
			int totalRead = 0;
			while (totalRead < contentLength) {
				int read = input.read(body, totalRead, contentLength - totalRead);
				if (read == -1) break;
				totalRead += read;
				if (System.currentTimeMillis() - startTime > readTimeout) {
					throw new java.net.SocketTimeoutException("Request body read timeout");
				}
			}
		}

		return new Request(method, path, query, contentType, body, headers, rawPathBytes);
	}
}
