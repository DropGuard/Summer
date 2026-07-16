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

	/**
	 * Per-thread configuration overrides installed by the test framework (e.g.
	 * {@code @TestProfile}). Keys are dotted paths in the original YAML key form
	 * (kebab/snake/dot), normalized on application so they line up with the
	 * camelCased section produced by {@link #normalizeKeys(Map)}. Overrides win
	 * over both YAML values and {@code @DefaultValue} defaults.
	 *
	 * <p>
	 * Thread-local (not static) so parallel test engines — Runtime on one virtual
	 * thread, AOT on another — never cross-contaminate profile state. Cleared by
	 * the framework after the container is built.
	 * </p>
	 */
	private static final ThreadLocal<Map<String, Object>> PROFILE_OVERRIDES = ThreadLocal
			.withInitial(LinkedHashMap::new);

	private ConfigBinder() {
	}

	public static void setDefaultValueResolver(DefaultValueResolver resolver) {
		defaultValueResolver = resolver;
	}

	/**
	 * Installs profile overrides for the current thread. Replaces any previously
	 * set overrides (call {@link #clearProfileOverrides()} when the container is
	 * disposed). Intended to be called by the test framework immediately before a
	 * container is built.
	 *
	 * @param overrides
	 *            dotted-path keys → values, in original YAML key form
	 */
	public static void setProfileOverrides(Map<String, Object> overrides) {
		Map<String, Object> copy = new LinkedHashMap<>(overrides);
		PROFILE_OVERRIDES.set(copy);
	}

	/** Removes any profile overrides for the current thread. */
	public static void clearProfileOverrides() {
		PROFILE_OVERRIDES.remove();
	}

	/**
	 * Returns the current thread's profile overrides (unmodifiable). Used by the
	 * test framework to derive the AOT container identity from the actual config
	 * content — so the identity and the binding read from the same source and can
	 * never drift.
	 *
	 * @return overrides map, or an empty map when none are set
	 */
	public static Map<String, Object> getProfileOverrides() {
		Map<String, Object> current = PROFILE_OVERRIDES.get();
		return current == null ? Map.of() : java.util.Collections.unmodifiableMap(current);
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
			applyProfileOverrides(section, prefix);
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

	/**
	 * Merges the current thread's profile overrides into the (already normalized)
	 * section map, just before Jackson conversion. Overrides are keyed by dotted
	 * path in the original YAML key form; each key is normalized to camelCase so it
	 * lands on the matching record component. Nested paths ({@code server.port})
	 * descend into nested maps. Only overrides under the requested {@code prefix}
	 * apply — an override for {@code server.port} is ignored when binding the
	 * {@code app} section.
	 *
	 * @param section
	 *            normalized section map (mutated in place)
	 * @param prefix
	 *            the binding prefix, or empty for the root
	 */
	@SuppressWarnings("unchecked")
	private static void applyProfileOverrides(Map<String, Object> section, String prefix) {
		Map<String, Object> overrides = PROFILE_OVERRIDES.get();
		if (overrides == null || overrides.isEmpty()) {
			return;
		}
		String scope = (prefix == null || prefix.isEmpty()) ? "" : prefix + ".";
		for (Map.Entry<String, Object> entry : overrides.entrySet()) {
			String key = entry.getKey();
			if (!scope.isEmpty() && !key.startsWith(scope)) {
				continue;
			}
			String relative = scope.isEmpty() ? key : key.substring(scope.length());
			if (relative.isEmpty()) {
				continue;
			}
			writeNested(section, splitDotted(relative), entry.getValue());
		}
	}

	private static String[] splitDotted(String key) {
		return key.split("\\.");
	}

	/**
	 * Walks {@code path} (already camelCased segments) into {@code target},
	 * creating intermediate {@link LinkedHashMap}s as needed, and sets the leaf to
	 * {@code value}.
	 */
	@SuppressWarnings("unchecked")
	private static void writeNested(Map<String, Object> target, String[] path, Object value) {
		Map<String, Object> current = target;
		for (int i = 0; i < path.length - 1; i++) {
			Object next = current.get(path[i]);
			if (!(next instanceof Map)) {
				next = new LinkedHashMap<String, Object>();
				current.put(path[i], next);
			}
			current = (Map<String, Object>) next;
		}
		current.put(path[path.length - 1], value);
	}
}
