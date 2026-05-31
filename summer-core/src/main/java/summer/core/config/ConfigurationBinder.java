package summer.core.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.InputStream;
import java.util.Map;
import summer.core.ErrorCode;
import summer.core.exception.ConfigurationException;

/**
 * Binds YAML configuration to Java records using Jackson.
 * 
 * <p>
 * This class provides methods to load YAML configuration files and bind them
 * to immutable Java records. It supports:
 * </p>
 * <ul>
 * <li>Simple property binding</li>
 * <li>Nested property binding with prefix extraction</li>
 * <li>Default values when configuration file is not found</li>
 * </ul>
 * 
 * <p>
 * Example usage:
 * </p>
 * 
 * <pre>{@code
 * // Simple binding
 * record ServerConfig(int port) {}
 * ServerConfig config = ConfigurationBinder.bind("application.yml", ServerConfig.class);
 * 
 * // With prefix
 * record JwtConfig(String secret) {}
 * JwtConfig config = ConfigurationBinder.bind("application.yml", JwtConfig.class, "jwt");
 * 
 * // With default value
 * ServerConfig config = ConfigurationBinder.bind("application.yml", ServerConfig.class, new ServerConfig(8080));
 * }</pre>
 */
public final class ConfigurationBinder {

	private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

	private ConfigurationBinder() {
	}

	/**
	 * Binds the entire YAML configuration file to the specified type.
	 * 
	 * @param classpathResource the classpath resource path
	 * @param type the target type to bind to
	 * @return the bound configuration object
	 * @throws ConfigurationException if the file cannot be parsed
	 */
	public static <T> T bind(String classpathResource, Class<T> type) {
		return bind(classpathResource, type, null, null);
	}

	/**
	 * Binds a section of the YAML configuration file (specified by prefix) to the
	 * specified type.
	 * 
	 * @param classpathResource the classpath resource path
	 * @param type the target type to bind to
	 * @param prefix the YAML section prefix (e.g., "jwt" for jwt.secret)
	 * @return the bound configuration object
	 * @throws ConfigurationException if the file cannot be parsed or prefix not
	 *         found
	 */
	public static <T> T bind(String classpathResource, Class<T> type, String prefix) {
		return bind(classpathResource, type, prefix, null);
	}

	/**
	 * Binds the entire YAML configuration file to the specified type with a default
	 * value.
	 * 
	 * @param classpathResource the classpath resource path
	 * @param type the target type to bind to
	 * @param defaultValue the default value to return if the file is not found
	 * @return the bound configuration object, or defaultValue if file not found
	 * @throws ConfigurationException if the file cannot be parsed
	 */
	public static <T> T bindOrDefault(String classpathResource, Class<T> type, T defaultValue) {
		return bind(classpathResource, type, null, defaultValue);
	}

	/**
	 * Binds a section of the YAML configuration file (specified by prefix) to the
	 * specified type with a default value.
	 * 
	 * @param classpathResource the classpath resource path
	 * @param type the target type to bind to
	 * @param prefix the YAML section prefix (e.g., "jwt" for jwt.secret)
	 * @param defaultValue the default value to return if the file is not found
	 * @return the bound configuration object, or defaultValue if file not found
	 * @throws ConfigurationException if the file cannot be parsed or prefix not
	 *         found
	 */
	public static <T> T bindOrDefault(String classpathResource, Class<T> type, String prefix, T defaultValue) {
		return bind(classpathResource, type, prefix, defaultValue);
	}

	/**
	 * Core binding method.
	 */
	@SuppressWarnings("unchecked")
	private static <T> T bind(String classpathResource, Class<T> type, String prefix, T defaultValue) {
		try (InputStream stream = Thread.currentThread().getContextClassLoader()
				.getResourceAsStream(classpathResource)) {
			if (stream == null) {
				if (defaultValue != null) {
					return defaultValue;
				}
				throw new ConfigurationException(ErrorCode.CONFIG_PARSE_ERROR,
						"Configuration file not found: " + classpathResource);
			}

			// Parse YAML into a Map
			Map<String, Object> root = YAML_MAPPER.readValue(stream, Map.class);

			// Extract section by prefix
			Map<String, Object> section = root;
			if (prefix != null && !prefix.isEmpty()) {
				section = extractSection(root, prefix);
			}

			// Bind to target type
			return YAML_MAPPER.convertValue(section, type);
		} catch (ConfigurationException e) {
			throw e;
		} catch (Exception e) {
			throw new ConfigurationException(ErrorCode.CONFIG_PARSE_ERROR,
					"Failed to parse YAML configuration '" + classpathResource + "': " + e.getMessage(), e);
		}
	}

	/**
	 * Extracts a nested section from the root map by prefix.
	 * 
	 * @param root the root map
	 * @param prefix the dot-separated prefix path
	 * @return the extracted section
	 * @throws ConfigurationException if the prefix path is not found
	 */
	@SuppressWarnings("unchecked")
	private static Map<String, Object> extractSection(Map<String, Object> root, String prefix) {
		String[] parts = prefix.split("\\.");
		Map<String, Object> current = root;

		for (String part : parts) {
			Object value = current.get(part);
			if (value == null) {
				throw new ConfigurationException(ErrorCode.CONFIG_PARSE_ERROR,
						"Configuration prefix '" + prefix + "' not found. Missing section: " + part);
			}
			if (!(value instanceof Map)) {
				throw new ConfigurationException(ErrorCode.CONFIG_PARSE_ERROR,
						"Configuration prefix '" + prefix + "' is not a map. Found: " + value.getClass().getSimpleName());
			}
			current = (Map<String, Object>) value;
		}

		return current;
	}
}
