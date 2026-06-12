package summer.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import summer.core.exception.BeanCreationException;
import summer.core.exception.ConfigurationException;
import summer.core.json.SummerObjectMapper;

/**
 * Shared YAML-to-record binding for {@code @ConfigurationProperties}.
 *
 * <p>
 * Both the runtime DI engine ({@code RuntimeBeanFactory}) and the AOT code
 * generator ({@code ConfigPropertiesGenerator}) delegate here instead of
 * duplicating the binding logic.
 * </p>
 */
public final class ConfigBinder {

	private static final ObjectMapper YAML_MAPPER = SummerObjectMapper.createYaml();
	private static final String YAML_RESOURCE = "application.yml";

	private static volatile DefaultValueResolver defaultValueResolver = (section, type) -> {
	};

	private ConfigBinder() {
	}

	public static void setDefaultValueResolver(DefaultValueResolver resolver) {
		defaultValueResolver = resolver;
	}

	/**
	 * Full binding pipeline: read {@code application.yml}, extract the prefix
	 * section, normalize keys, apply {@code @DefaultValue} defaults, and convert to
	 * the target record type via Jackson.
	 *
	 * @param prefix
	 *            the YAML prefix (e.g. "app", "server.tls"), or empty for root
	 * @param targetType
	 *            the record class to bind to
	 * @return the bound instance
	 */
	@SuppressWarnings("unchecked")
	public static <T> T bind(String prefix, Class<T> targetType) {
		try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(YAML_RESOURCE)) {
			Map<String, Object> section;
			if (stream != null) {
				Map<String, Object> root = YAML_MAPPER.readValue(stream, Map.class);
				section = prefix != null && !prefix.isEmpty() ? extractSection(root, prefix) : root;
				if (section == null) {
					section = new LinkedHashMap<>();
				}
				section = normalizeKeys(section);
			} else {
				section = new LinkedHashMap<>();
			}
			applyDefaults(section, targetType);
			return YAML_MAPPER.convertValue(section, targetType);
		} catch (ConfigurationException e) {
			throw e;
		} catch (Exception e) {
			throw new BeanCreationException("Failed to bind @ConfigurationProperties: " + targetType.getName(), e);
		}
	}

	/**
	 * Extracts a nested section from the YAML root map by splitting the prefix on
	 * dots and walking each level.
	 *
	 * @return the section map, or null if any level is missing
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, Object> extractSection(Map<String, Object> root, String prefix) {
		Map<String, Object> current = root;
		for (String part : prefix.split("\\.")) {
			Object value = current.get(part);
			if (!(value instanceof Map))
				return null;
			current = (Map<String, Object>) value;
		}
		return current;
	}

	/**
	 * Recursively converts all map keys from kebab-case/snake-case/dot-separated to
	 * camelCase so they match Java record component names.
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

	/**
	 * Converts a single key from kebab-case, snake_case, or dot.separated to
	 * camelCase.
	 */
	public static String toCamelCase(String key) {
		if (key == null || key.isEmpty())
			return key;
		String[] parts = key.split("[-_.]");
		if (parts.length == 1)
			return key;
		StringBuilder sb = new StringBuilder(parts[0].toLowerCase());
		for (int i = 1; i < parts.length; i++) {
			if (!parts[i].isEmpty()) {
				sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1).toLowerCase());
			}
		}
		return sb.toString();
	}

	/**
	 * Applies {@link DefaultValue} defaults for record components that are missing
	 * from the section map. Delegates to the configured
	 * {@link DefaultValueResolver}.
	 */
	public static void applyDefaults(Map<String, Object> section, Class<?> type) {
		defaultValueResolver.applyDefaults(section, type);
	}
}
