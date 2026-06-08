package summer.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import summer.core.ErrorCode;
import summer.core.config.ConfigurationBinder;
import summer.core.exception.ConfigurationException;
import summer.core.json.SummerObjectMapper;

/**
 * Loads YAML configuration and delegates to
 * {@link ConfigurationBinder#bindWithDefaults(Map, Class, String)}.
 *
 * <p>
 * If a prefix section is absent from the YAML file, binding proceeds with an
 * empty map. The binder applies {@code @DefaultValue} defaults and validates
 * required fields in a single pass.
 * </p>
 */
public class ConfigurationLoader {

	private static final ObjectMapper YAML_MAPPER = SummerObjectMapper.createYaml();

	/**
	 * Binds the entire YAML configuration file to the specified type.
	 */
	public <T> T bind(String classpathResource, Class<T> type) {
		return bind(classpathResource, type, null, null);
	}

	/**
	 * Binds a section of the YAML configuration file to the specified type.
	 */
	public <T> T bind(String classpathResource, Class<T> type, String prefix) {
		return bind(classpathResource, type, prefix, null);
	}

	/**
	 * Binds the entire YAML configuration file with a fallback default.
	 */
	public <T> T bindOrDefault(String classpathResource, Class<T> type, T defaultValue) {
		return bind(classpathResource, type, null, defaultValue);
	}

	/**
	 * Binds a section of the YAML configuration file with a fallback default.
	 */
	public <T> T bindOrDefault(String classpathResource, Class<T> type, String prefix, T defaultValue) {
		return bind(classpathResource, type, prefix, defaultValue);
	}

	@SuppressWarnings("unchecked")
	private <T> T bind(String classpathResource, Class<T> type, String prefix, T defaultValue) {
		try (InputStream stream = Thread.currentThread().getContextClassLoader()
				.getResourceAsStream(classpathResource)) {
			Map<String, Object> root;
			if (stream == null) {
				if (defaultValue != null) {
					return defaultValue;
				}
				root = new LinkedHashMap<>();
			} else {
				root = YAML_MAPPER.readValue(stream, Map.class);
			}

			Map<String, Object> section = root;
			if (prefix != null && !prefix.isEmpty()) {
				section = ConfigurationBinder.extractSection(root, prefix);
			}

			if (section == null) {
				if (defaultValue != null) {
					return defaultValue;
				}
				section = new LinkedHashMap<>();
			}
			section = ConfigurationBinder.normalizeKeys(section);
			return ConfigurationBinder.bindWithDefaults(section, type, classpathResource);
		} catch (ConfigurationException e) {
			throw e;
		} catch (Exception e) {
			throw new ConfigurationException(ErrorCode.CONFIG_PARSE_ERROR,
					"Failed to parse YAML configuration '" + classpathResource + "': " + e.getMessage(), e);
		}
	}
}
