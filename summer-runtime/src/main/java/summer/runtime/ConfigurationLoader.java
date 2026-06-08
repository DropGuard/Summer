package summer.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import summer.core.ErrorCode;
import summer.core.config.ConfigurationBinder;
import summer.core.config.DefaultValue;
import summer.core.exception.ConfigurationException;
import summer.core.json.SummerObjectMapper;

/**
 * Loads YAML configuration and delegates to
 * {@link ConfigurationBinder#bind(Map, Class)}.
 *
 * <p>
 * If a prefix section is absent from the YAML file, binding proceeds with an
 * empty map. Record components annotated with {@code @DefaultValue} get their
 * declared values; components without cause a clear "missing required property"
 * error from the binder.
 * </p>
 *
 * <p>
 * This is a framework infrastructure bean provided by
 * {@link RuntimeInfrastructureConfiguration}.
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
				// File absent — start with an empty root so @DefaultValue
				// can supply values for fields that declare it.
				root = new java.util.LinkedHashMap<>();
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
				// Section absent — proceed with an empty map.
				section = new LinkedHashMap<>();
			}
			section = ConfigurationBinder.normalizeKeys(section);
			applyDefaults(section, type);
			if (type.isRecord()) {
				validateAllFieldsPresent(section, type, classpathResource);
			}
			return ConfigurationBinder.bind(section, type);
		} catch (ConfigurationException e) {
			throw e;
		} catch (Exception e) {
			throw new ConfigurationException(ErrorCode.CONFIG_PARSE_ERROR,
					"Failed to parse YAML configuration '" + classpathResource + "': " + e.getMessage(), e);
		}
	}

	private static <T> void validateAllFieldsPresent(Map<String, Object> section, Class<T> type, String resource) {
		List<String> missing = new ArrayList<>();
		for (RecordComponent component : type.getRecordComponents()) {
			if (!section.containsKey(component.getName()) && component.getAnnotation(DefaultValue.class) == null) {
				missing.add(component.getName());
			}
		}
		if (!missing.isEmpty()) {
			throw new ConfigurationException(ErrorCode.CONFIG_PARSE_ERROR, "Missing required configuration properties "
					+ missing + " for " + type.getSimpleName() + " in '" + resource + "'");
		}
	}

	private static <T> void applyDefaults(Map<String, Object> section, Class<T> type) {
		if (!type.isRecord())
			return;
		for (RecordComponent component : type.getRecordComponents()) {
			DefaultValue ann = component.getAnnotation(DefaultValue.class);
			if (ann != null && !section.containsKey(component.getName())) {
				section.put(component.getName(), parseDefaultValue(ann.value(), component.getType()));
			}
		}
	}

	private static Object parseDefaultValue(String value, Class<?> targetType) {
		return TypeConverter.convert(value, targetType);
	}
}
