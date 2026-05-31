package summer.core.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.InputStream;
import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.Map;
import summer.core.ErrorCode;
import summer.core.exception.ConfigurationException;

/**
 * Binds YAML configuration to Java records using Jackson.
 *
 * <p>
 * This class provides methods to load YAML configuration files and bind them to
 * immutable Java records. It supports:
 * </p>
 * <ul>
 * <li>Simple property binding</li>
 * <li>Nested property binding with prefix extraction</li>
 * <li>Default values via {@link DefaultValue} annotation</li>
 * <li>Default values when configuration file is not found</li>
 * <li>Relaxed binding (kebab-case, snake_case, SCREAMING_SNAKE_CASE to
 * camelCase)</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>{@code
 * // Simple binding
 * record ServerConfig(int port) {
 * }
 * ServerConfig config = ConfigurationBinder.bind("application.yml", ServerConfig.class);
 *
 * // With prefix
 * record JwtConfig(String secret) {
 * }
 * JwtConfig config = ConfigurationBinder.bind("application.yml", JwtConfig.class, "jwt");
 *
 * // With @DefaultValue
 * record JwtConfig(String secret, @DefaultValue("3600000") long expiration) {
 * }
 * JwtConfig config = ConfigurationBinder.bind("application.yml", JwtConfig.class, "jwt");
 *
 * // With default value (entire object)
 * ServerConfig config = ConfigurationBinder.bind("application.yml", ServerConfig.class, new ServerConfig(8080));
 * }</pre>
 *
 * <p>
 * <strong>Relaxed binding:</strong> YAML keys are automatically converted to
 * camelCase to match Java naming conventions. For example,
 * {@code connection-timeout} in YAML will bind to {@code connectionTimeout} in
 * a record.
 * </p>
 */
public final class ConfigurationBinder {

	private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

	private ConfigurationBinder() {
	}

	/**
	 * Binds the entire YAML configuration file to the specified type.
	 *
	 * @param classpathResource
	 *            the classpath resource path
	 * @param type
	 *            the target type to bind to
	 * @return the bound configuration object
	 * @throws ConfigurationException
	 *             if the file cannot be parsed
	 */
	public static <T> T bind(String classpathResource, Class<T> type) {
		return bind(classpathResource, type, null, null);
	}

	/**
	 * Binds a section of the YAML configuration file (specified by prefix) to the
	 * specified type.
	 *
	 * @param classpathResource
	 *            the classpath resource path
	 * @param type
	 *            the target type to bind to
	 * @param prefix
	 *            the YAML section prefix (e.g., "jwt" for jwt.secret)
	 * @return the bound configuration object
	 * @throws ConfigurationException
	 *             if the file cannot be parsed or prefix not found
	 */
	public static <T> T bind(String classpathResource, Class<T> type, String prefix) {
		return bind(classpathResource, type, prefix, null);
	}

	/**
	 * Binds the entire YAML configuration file to the specified type with a default
	 * value.
	 *
	 * @param classpathResource
	 *            the classpath resource path
	 * @param type
	 *            the target type to bind to
	 * @param defaultValue
	 *            the default value to return if the file is not found
	 * @return the bound configuration object, or defaultValue if file not found
	 * @throws ConfigurationException
	 *             if the file cannot be parsed
	 */
	public static <T> T bindOrDefault(String classpathResource, Class<T> type, T defaultValue) {
		return bind(classpathResource, type, null, defaultValue);
	}

	/**
	 * Binds a section of the YAML configuration file (specified by prefix) to the
	 * specified type with a default value.
	 *
	 * @param classpathResource
	 *            the classpath resource path
	 * @param type
	 *            the target type to bind to
	 * @param prefix
	 *            the YAML section prefix (e.g., "jwt" for jwt.secret)
	 * @param defaultValue
	 *            the default value to return if the file is not found
	 * @return the bound configuration object, or defaultValue if file not found
	 * @throws ConfigurationException
	 *             if the file cannot be parsed or prefix not found
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

			// Relaxed binding: normalize keys to camelCase
			section = normalizeKeys(section);

			// Apply @DefaultValue annotations for records
			if (type.isRecord()) {
				applyDefaults(section, type);
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
	 * Applies default values from {@link DefaultValue} annotations for record
	 * components that are not present in the configuration section.
	 *
	 * @param section
	 *            the configuration section map
	 * @param type
	 *            the record type
	 */
	private static <T> void applyDefaults(Map<String, Object> section, Class<T> type) {
		for (RecordComponent component : type.getRecordComponents()) {
			DefaultValue ann = component.getAnnotation(DefaultValue.class);
			if (ann != null && !section.containsKey(component.getName())) {
				section.put(component.getName(), parseDefaultValue(ann.value(), component.getType()));
			}
		}
	}

	/**
	 * Parses a default value string to the target type.
	 *
	 * @param value
	 *            the default value string
	 * @param targetType
	 *            the target type
	 * @return the parsed value
	 */
	private static Object parseDefaultValue(String value, Class<?> targetType) {
		if (targetType == String.class) {
			return value;
		} else if (targetType == int.class || targetType == Integer.class) {
			return Integer.parseInt(value);
		} else if (targetType == long.class || targetType == Long.class) {
			return Long.parseLong(value);
		} else if (targetType == boolean.class || targetType == Boolean.class) {
			return Boolean.parseBoolean(value);
		} else if (targetType == double.class || targetType == Double.class) {
			return Double.parseDouble(value);
		} else if (targetType == float.class || targetType == Float.class) {
			return Float.parseFloat(value);
		} else if (targetType == short.class || targetType == Short.class) {
			return Short.parseShort(value);
		} else if (targetType == byte.class || targetType == Byte.class) {
			return Byte.parseByte(value);
		} else {
			throw new ConfigurationException(ErrorCode.CONFIG_PARSE_ERROR,
					"Unsupported type for @DefaultValue: " + targetType.getName());
		}
	}

	/**
	 * Extracts a nested section from the root map by prefix.
	 *
	 * @param root
	 *            the root map
	 * @param prefix
	 *            the dot-separated prefix path
	 * @return the extracted section
	 * @throws ConfigurationException
	 *             if the prefix path is not found
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
				throw new ConfigurationException(ErrorCode.CONFIG_PARSE_ERROR, "Configuration prefix '" + prefix
						+ "' is not a map. Found: " + value.getClass().getSimpleName());
			}
			current = (Map<String, Object>) value;
		}

		return current;
	}

	/**
	 * Normalizes all keys in a map to camelCase for relaxed binding. Handles
	 * kebab-case (context-path), snake_case (context_path), and
	 * SCREAMING_SNAKE_CASE (CONTEXT_PATH).
	 *
	 * @param map
	 *            the map to normalize
	 * @return a new map with normalized keys
	 */
	private static Map<String, Object> normalizeKeys(Map<String, Object> map) {
		Map<String, Object> result = new LinkedHashMap<>();
		for (Map.Entry<String, Object> entry : map.entrySet()) {
			String key = toCamelCase(entry.getKey());
			Object value = entry.getValue();
			// Recursively normalize nested maps
			if (value instanceof Map<?, ?> nested) {
				value = normalizeKeys((Map<String, Object>) nested);
			}
			result.put(key, value);
		}
		return result;
	}

	/**
	 * Converts a key to camelCase.
	 *
	 * <p>
	 * Examples:
	 * </p>
	 * <ul>
	 * <li>{@code context-path} → {@code contextPath}</li>
	 * <li>{@code context_path} → {@code contextPath}</li>
	 * <li>{@code CONTEXT_PATH} → {@code contextPath}</li>
	 * <li>{@code context.path} → {@code contextPath}</li>
	 * </ul>
	 *
	 * @param key
	 *            the key to convert
	 * @return the camelCase key
	 */
	private static String toCamelCase(String key) {
		if (key == null || key.isEmpty()) {
			return key;
		}

		// Split by common delimiters
		String[] parts = key.split("[-_.]");
		if (parts.length == 1) {
			return key; // Already camelCase or single word, return as-is
		}

		StringBuilder sb = new StringBuilder(parts[0].toLowerCase());
		for (int i = 1; i < parts.length; i++) {
			if (!parts[i].isEmpty()) {
				sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1).toLowerCase());
			}
		}
		return sb.toString();
	}
}
