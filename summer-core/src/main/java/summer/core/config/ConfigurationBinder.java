package summer.core.config;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import summer.core.ErrorCode;
import summer.core.exception.ConfigurationException;
import summer.core.json.SummerObjectMapper;
/**
 * Binds YAML configuration to Java records using Jackson.
 *
 * <p>
 * Loads {@code application.yml} from the classpath, normalizes keys to
 * camelCase (relaxed binding), and binds to the target type. Missing fields are
 * filled from {@link DefaultValue} annotations on record components.
 * </p>
 */
public final class ConfigurationBinder {
	private static final ObjectMapper YAML_MAPPER = SummerObjectMapper.createYaml();
	private static final String RESOURCE = "application.yml";
	/** Default resolver: AOT-based (no reflection, suitable for native image). */
	private static DefaultValueResolver defaultResolver = new AotDefaultValueResolver();
	private ConfigurationBinder() {
	}
	/**
	 * Sets the default {@link DefaultValueResolver} used by the no-resolver
	 * {@link #bind(Class, String)} overload.
	 *
	 * @param resolver
	 *            the resolver to use (must not be null)
	 */
	public static void setDefaultResolver(DefaultValueResolver resolver) {
		if (resolver == null) {
			throw new IllegalArgumentException("resolver must not be null");
		}
		defaultResolver = resolver;
	}

	/** Binds a prefixed section of the YAML to the specified type. */
	public static <T> T bind(Class<T> type, String prefix) {
		return bind(type, prefix, defaultResolver);
	}
	/**
	 * Binds a prefixed section of the YAML to the specified type, using the given
	 * resolver for {@link DefaultValue} annotations.
	 */
	public static <T> T bind(Class<T> type, String prefix, DefaultValueResolver resolver) {
		try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(RESOURCE)) {
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
			resolver.applyDefaults(section, type);
			return YAML_MAPPER.convertValue(section, type);
		} catch (ConfigurationException e) {
			throw e;
		} catch (Exception e) {
			throw new ConfigurationException(ErrorCode.CONFIG_PARSE_ERROR,
					"Failed to bind configuration: " + e.getMessage(), e);
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> extractSection(Map<String, Object> root, String prefix) {
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

	private static Map<String, Object> normalizeKeys(Map<String, Object> map) {
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
