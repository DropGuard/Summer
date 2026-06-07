package summer.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import summer.core.config.ConfigurationBinder;

/**
 * Immutable server configuration bound from {@code application.yml}.
 *
 * <p>
 * Example YAML:
 *
 * <pre>{@code
 * server:
 *   port: 8081
 *   websocket:
 *     allowed-origins:
 *       - "https://example.com"
 *       - "https://app.example.com"
 * }</pre>
 *
 * @param port
 *            the HTTP port to listen on (default: 8080)
 * @param connectionTimeout
 *            connection timeout in ms
 * @param maxBodySize
 *            max request body size in bytes
 * @param readTimeout
 *            read timeout in ms
 * @param allowedOrigins
 *            list of allowed WebSocket origins (empty = same-origin only, "*" =
 *            allow all)
 * @param maxWebSocketFrameSize
 *            max WebSocket frame size in bytes (default: 65536)
 */
public record ServerConfig(@JsonProperty("port") int port, @JsonProperty("connectionTimeout") int connectionTimeout,
		@JsonProperty("maxBodySize") int maxBodySize, @JsonProperty("readTimeout") int readTimeout,
		@JsonProperty("allowed-origins") List<String> allowedOrigins,
		@JsonProperty("maxWebSocketFrameSize") int maxWebSocketFrameSize) {

	/**
	 * Sensible default configuration. (Default Max Body: 10MB, Read Timeout: 10s,
	 * Max WebSocket Frame: 64KB)
	 */
	public static final ServerConfig DEFAULT = new ServerConfig(8080, 30000, 10485760, 10000, List.of(), 65536);

	public static ServerConfig fromYaml() {
		return ConfigurationBinder.bindOrDefault("application.yml", ServerConfig.class, DEFAULT);
	}

	/**
	 * Checks if the given Origin is allowed for WebSocket connections.
	 *
	 * @param origin
	 *            the Origin header value (may be null)
	 * @param requestHost
	 *            the request Host header value
	 * @return true if the origin is allowed
	 */
	public boolean isOriginAllowed(String origin, String requestHost) {
		// If no origins configured, enforce same-origin check
		if (allowedOrigins.isEmpty()) {
			return isSameOrigin(origin, requestHost);
		}
		// Wildcard allows all origins
		if (allowedOrigins.contains("*")) {
			return true;
		}
		// Check against allowed list
		return origin != null && allowedOrigins.contains(origin);
	}

	private boolean isSameOrigin(String origin, String requestHost) {
		if (origin == null || requestHost == null) {
			return false;
		}
		try {
			// Extract host and port from origin
			java.net.URI uri = java.net.URI.create(origin);
			String originHost = uri.getHost();
			if (originHost == null) {
				return false;
			}
			int originPort = uri.getPort() != -1 ? uri.getPort() : "https".equals(uri.getScheme()) ? 443 : 80;

			// Parse requestHost to extract host and port
			String reqHost;
			int reqPort;
			int colonIndex = requestHost.lastIndexOf(':');
			if (colonIndex != -1) {
				reqHost = requestHost.substring(0, colonIndex);
				reqPort = Integer.parseInt(requestHost.substring(colonIndex + 1));
			} else {
				reqHost = requestHost;
				reqPort = 80;
			}

			// Compare both host and port
			return reqHost.equals(originHost) && reqPort == originPort;
		} catch (Exception e) {
			return false;
		}
	}
}
