package summer.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import summer.core.ErrorCode;
import summer.core.exception.ConfigurationException;
import summer.core.json.SummerObjectMapper;

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
 * <li>Default values when configuration file is not found</li>
 * <li>Relaxed binding (kebab-case, snake_case, SCREAMING_SNAKE_CASE to
 * camelCase)</li>
 * <li>{@link DefaultValue} processing via
 * {@link #bindWithDefaults(Map, Class, String)}</li>
 * </ul>
 */
public final class ConfigurationBinder {
	private static final ObjectMapper YAML_MAPPER = SummerObjectMapper.createYaml();

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
	 * Binds a pre-processed map to the specified type. The map should already have
	 * keys normalized to camelCase and defaults applied.
	 *
	 * @param section
	 *            the configuration map
	 * @param type
	 *            the target type to bind to
	 * @return the bound configuration object
	 */
	public static <T> T bind(Map<String, Object> section, Class<T> type) {
		return YAML_MAPPER.convertValue(section, type);
	}

	/**
	 * Binds a pre-processed map to the specified type, applying
	 * {@link DefaultValue} defaults.
	 *
	 * <p>
	 * For record types, this method fills missing keys from
	 * {@code @DefaultValue} annotations. Fields without {@code @DefaultValue}
	 * that are absent from YAML are set to {@code null}. Use a
	 * {@code Validator} to enforce business constraints after binding.
	 * </p>
	 *
	 * @param section
	 *            the configuration map (keys should already be normalized to
	 *            camelCase)
	 * @param type
	 *            the target type to bind to
	 * @return the bound configuration object
	 */
	public static <T> T bindWithDefaults(Map<String, Object> section, Class<T> type) {
		if (type.isRecord()) {
			for (java.lang.reflect.RecordComponent component : type.getRecordComponents()) {
				DefaultValue ann = component.getAnnotation(DefaultValue.class);
				if (!section.containsKey(component.getName())) {
					if (ann != null) {
						section.put(component.getName(), TypeConverter.convert(ann.value(), component.getType()));
					} else {
						section.put(component.getName(), null);
					}
				}
			}
		}
		return YAML_MAPPER.convertValue(section, type);
	}

	/**
	 * Loads a YAML resource, extracts a prefix section, normalizes keys, fills
	 * missing keys from {@code defaults}, and binds to the target type.
	 *
	 * <p>
	 * Used by AOT-generated code to supply {@code @DefaultValue} values without
	 * runtime reflection.
	 * </p>
	 */
	@SuppressWarnings("unchecked")
	public static <T> T bindWithDefaults(String classpathResource, Class<T> type, String prefix,
			java.util.Map<String, Object> defaults) {
		try (InputStream stream = Thread.currentThread().getContextClassLoader()
				.getResourceAsStream(classpathResource)) {
			Map<String, Object> section;
			if (stream != null) {
				Map<String, Object> root = YAML_MAPPER.readValue(stream, Map.class);
				section = root;
				if (prefix != null && !prefix.isEmpty()) {
					section = extractSection(root, prefix);
				}
				if (section == null) {
					section = new LinkedHashMap<>();
				}
				section = normalizeKeys(section);
			} else {
				section = new LinkedHashMap<>();
			}
			// Fill missing keys from defaults
			for (java.util.Map.Entry<String, Object> entry : defaults.entrySet()) {
				section.putIfAbsent(entry.getKey(), entry.getValue());
			}
			return YAML_MAPPER.convertValue(section, type);
		} catch (ConfigurationException e) {
			throw e;
		} catch (Exception e) {
			throw new ConfigurationException(ErrorCode.CONFIG_PARSE_ERROR,
					"Failed to bind configuration from '" + classpathResource + "': " + e.getMessage(), e);
		}
	}

	/**
	 * Loads a YAML resource, extracts an optional prefix section, and normalizes
	 * keys. Does not apply {@link DefaultValue} defaults &mdash; callers that need
	 * defaults should enrich the map before passing it to
	 * {@link #bind(Map, Class)}.
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

			Map<String, Object> root = YAML_MAPPER.readValue(stream, Map.class);

			Map<String, Object> section = root;
			if (prefix != null && !prefix.isEmpty()) {
				section = extractSection(root, prefix);
			}

			if (section == null) {
				if (defaultValue != null) {
					return defaultValue;
				}
				throw new ConfigurationException(ErrorCode.CONFIG_PARSE_ERROR,
						"Configuration prefix '" + prefix + "' not found in '" + classpathResource + "'");
			}

			section = normalizeKeys(section);
			return YAML_MAPPER.convertValue(section, type);
		} catch (ConfigurationException e) {
			throw e;
		} catch (Exception e) {
			throw new ConfigurationException(ErrorCode.CONFIG_PARSE_ERROR,
					"Failed to parse YAML configuration '" + classpathResource + "': " + e.getMessage(), e);
		}
	}

	/**
	 * Extracts a nested section from the root map by prefix. Returns {@code null}
	 * if the prefix does not exist.
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, Object> extractSection(Map<String, Object> root, String prefix) {
		String[] parts = prefix.split("\\.");
		Map<String, Object> current = root;

		for (String part : parts) {
			Object value = current.get(part);
			if (value == null) {
				return null;
			}
			if (!(value instanceof Map)) {
				return null;
			}
			current = (Map<String, Object>) value;
		}

		return current;
	}

	/**
	 * Normalizes all keys in a map to camelCase for relaxed binding.
	 */
	public static Map<String, Object> normalizeKeys(Map<String, Object> map) {
		Map<String, Object> result = new LinkedHashMap<>();
		for (Map.Entry<String, Object> entry : map.entrySet()) {
			String key = toCamelCase(entry.getKey());
			Object value = entry.getValue();
			if (value instanceof Map<?, ?> nested) {
				value = normalizeKeys((Map<String, Object>) nested);
			}
			result.put(key, value);
		}
		return result;
	}

	private static String toCamelCase(String key) {
		if (key == null || key.isEmpty()) {
			return key;
		}

		String[] parts = key.split("[-_.]");
		if (parts.length == 1) {
			return key;
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
