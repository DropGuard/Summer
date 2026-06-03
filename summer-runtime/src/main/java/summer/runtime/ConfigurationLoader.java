package summer.runtime;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.InputStream;
import java.lang.reflect.RecordComponent;
import java.util.Map;
import summer.core.Component;
import summer.core.ErrorCode;
import summer.core.config.ConfigurationBinder;
import summer.core.config.DefaultValue;
import summer.core.exception.ConfigurationException;

/**
 * Loads YAML configuration and applies {@link DefaultValue} defaults via
 * reflection before delegating to {@link ConfigurationBinder#bind(Map, Class)}.
 *
 * <p>
 * This class owns the reflective record-component inspection that was previously
 * in {@code ConfigurationBinder}. The binder itself is reflection-free.
 * </p>
 */
@Component
public class ConfigurationLoader {

	private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

	/**
	 * Binds the entire YAML configuration file to the specified type, applying
	 * {@link DefaultValue} defaults for record components.
	 */
	public <T> T bind(String classpathResource, Class<T> type) {
		return bind(classpathResource, type, null, null);
	}

	/**
	 * Binds a section of the YAML configuration file to the specified type,
	 * applying {@link DefaultValue} defaults for record components.
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
				section = ConfigurationBinder.extractSection(root, prefix);
			}

			section = ConfigurationBinder.normalizeKeys(section);

			if (type.isRecord()) {
				applyDefaults(section, type);
			}

			return ConfigurationBinder.bind(section, type);
		} catch (ConfigurationException e) {
			throw e;
		} catch (Exception e) {
			throw new ConfigurationException(ErrorCode.CONFIG_PARSE_ERROR,
					"Failed to parse YAML configuration '" + classpathResource + "': " + e.getMessage(), e);
		}
	}

	private static <T> void applyDefaults(Map<String, Object> section, Class<T> type) {
		for (RecordComponent component : type.getRecordComponents()) {
			DefaultValue ann = component.getAnnotation(DefaultValue.class);
			if (ann != null && !section.containsKey(component.getName())) {
				section.put(component.getName(), parseDefaultValue(ann.value(), component.getType()));
			}
		}
	}

	private static Object parseDefaultValue(String value, Class<?> targetType) {
		if (targetType == String.class) return value;
		if (targetType == int.class || targetType == Integer.class) return Integer.parseInt(value);
		if (targetType == long.class || targetType == Long.class) return Long.parseLong(value);
		if (targetType == boolean.class || targetType == Boolean.class) return Boolean.parseBoolean(value);
		if (targetType == double.class || targetType == Double.class) return Double.parseDouble(value);
		if (targetType == float.class || targetType == Float.class) return Float.parseFloat(value);
		if (targetType == short.class || targetType == Short.class) return Short.parseShort(value);
		if (targetType == byte.class || targetType == Byte.class) return Byte.parseByte(value);
		throw new ConfigurationException(ErrorCode.CONFIG_PARSE_ERROR,
				"Unsupported type for @DefaultValue: " + targetType.getName());
	}
}
