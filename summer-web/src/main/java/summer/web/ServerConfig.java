package summer.web;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Immutable server configuration bound from {@code application.yml}.
 *
 * <p>
 * Example YAML:
 * 
 * <pre>{@code
 * server:
 *   port: 8081
 * }</pre>
 *
 * @param port
 *            the HTTP port to listen on (default: 8080)
 */
public record ServerConfig(@JsonProperty("port") int port, @JsonProperty("connectionTimeout") int connectionTimeout,
		@JsonProperty("maxBodySize") int maxBodySize, @JsonProperty("readTimeout") int readTimeout) {

	/**
	 * Sensible default configuration. (Default Max Body: 10MB, Read Timeout: 10s)
	 */
	public static final ServerConfig DEFAULT = new ServerConfig(8080, 30000, 10485760, 10000);
}
