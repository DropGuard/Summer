package summer.core.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.InputStream;
import summer.core.ErrorCode;
import summer.core.exception.ConfigurationException;

/**
 * A lightweight YAML configuration loader that maps {@code application.yml}
 * directly to immutable Java Records using Jackson's YAML dataformat module.
 *
 * <p>
 * Usage:
 *
 * <pre>{@code
 * record ServerConfig(int port) {
 * }
 *
 * ServerConfig config = YamlConfigLoader.loadOrDefault("application.yml", ServerConfig.class, new ServerConfig(8080));
 * }</pre>
 */
public final class YamlConfigLoader {

	private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

	private YamlConfigLoader() {
	}

	/**
	 * Loads a YAML classpath resource and deserializes it into a Java Record.
	 * Returns the default value if the file is not found on the classpath.
	 * Throws {@link ConfigurationException} if the file exists but cannot be parsed.
	 */
	public static <T> T loadOrDefault(String classpathResource, Class<T> recordType, T defaultValue) {
		try (InputStream stream = Thread.currentThread().getContextClassLoader()
				.getResourceAsStream(classpathResource)) {
			if (stream == null) {
				return defaultValue;
			}
			return YAML_MAPPER.readValue(stream, recordType);
		} catch (Exception e) {
			throw new ConfigurationException(ErrorCode.CONFIG_PARSE_ERROR,
					"Failed to parse YAML configuration '" + classpathResource + "': " + e.getMessage(), e);
		}
	}
}
