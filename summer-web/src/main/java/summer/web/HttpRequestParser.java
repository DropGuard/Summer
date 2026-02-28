package summer.web;

import java.io.InputStream;

/**
 * Parses raw InputStream bytes from a TCP socket into a structured HTTP
 * Request.
 */
public class HttpRequestParser {

    public static Request parse(InputStream input) {
        // Simple request parsing (for demonstration purposes only)
        // In a real framework, this would parse HTTP headers, body, URL decoding etc.
        String method = "GET";
        String path = "/";
        String query = "";
        byte[] body = new byte[0];

        return new Request(method, path, query, body);
    }
}
