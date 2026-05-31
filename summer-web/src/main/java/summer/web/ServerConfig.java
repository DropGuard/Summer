package summer.web;

import com.fasterxml.jackson.annotation.JsonProperty;
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
 */
public record ServerConfig(@JsonProperty("port") int port, @JsonProperty("connectionTimeout") int connectionTimeout,
		@JsonProperty("maxBodySize") int maxBodySize, @JsonProperty("readTimeout") int readTimeout) {

	/**
	 * Sensible default configuration. (Default Max Body: 10MB, Read Timeout: 10s)
	 */
	public static final ServerConfig DEFAULT = new ServerConfig(8080, 30000, 10485760, 10000);

	public static ServerConfig fromYaml() {
		return ConfigurationBinder.bindOrDefault("application.yml", ServerConfig.class, DEFAULT);
	}
}
