package com.github.dropguard.summer.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dropguard.summer.core.exception.BeanCreationException;
import com.github.dropguard.summer.core.exception.ConfigurationException;
import com.github.dropguard.summer.core.json.SummerObjectMapper;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared YAML-to-record binding for {@code @ConfigurationProperties}.
 *
 * <p>
 * Both the runtime DI engine ({@code RuntimeBeanFactory}) and the AOT code
 * generator ({@code ConfigPropertiesGenerator}) delegate here instead of
 * duplicating the binding logic. Profile overrides and {@code @DefaultValue}
 * defaults are carried explicitly in a {@link BindingContext} rather than a
 * {@code ThreadLocal}, so the generated container's identity (derived from the
 * override content) and the binding read from the same source and can never
 * drift, and parallel engines (Runtime on one virtual thread, AOT on another)
 * never cross-contaminate profile state.
 * </p>
 */
public final class ConfigBinder {

	private static final ObjectMapper YAML_MAPPER = SummerObjectMapper.createYaml();
	private static final String YAML_RESOURCE = "application.yml";

	private ConfigBinder() {
	}

	/**
	 * Immutable carrier for the two inputs that can alter a
	 * {@code @ConfigurationProperties} binding beyond the YAML itself:
	 *
	 * <ul>
	 * <li>{@code defaults} — pre-converted {@code @DefaultValue} values, keyed by
	 * record component name. Supplied by the caller (runtime extracts them
	 * reflectively; AOT emits them inline via
	 * {@link com.github.dropguard.summer.core.util.TypeConverter}).</li>
	 * <li>{@code overrides} — per-profile overrides, keyed by dotted YAML path, in
	 * the original YAML key form. Baked in at code-generation time from
	 * {@code @TestProfile} so no runtime {@code ThreadLocal} is needed.</li>
	 * </ul>
	 *
	 * <p>
	 * Both win over YAML-absent fields; overrides win over defaults. The AOT
	 * generator builds these literals at generation time, which is also what it
	 * hashes to derive the generated container's identity.
	 * </p>
	 */
	public static final class BindingContext {

		private final Map<String, Object> defaults;
		private final Map<String, Object> overrides;

		private BindingContext(Map<String, Object> defaults, Map<String, Object> overrides) {
			this.defaults = defaults != null ? defaults : Map.of();
			this.overrides = overrides != null ? overrides : Map.of();
		}

		/** No defaults, no overrides — plain YAML binding. */
		public static BindingContext of() {
			return new BindingContext(Map.of(), Map.of());
		}

		/** Only profile overrides (no {@code @DefaultValue} metadata). */
		public static BindingContext of(Map<String, Object> overrides) {
			return new BindingContext(Map.of(), overrides);
		}

		/** Default values and profile overrides together. */
		public static BindingContext of(Map<String, Object> defaults, Map<String, Object> overrides) {
			return new BindingContext(defaults, overrides);
		}

		/**
		 * Pre-converted {@code @DefaultValue} values, keyed by record component name.
		 */
		public Map<String, Object> defaults() {
			return defaults;
		}

		/** Profile overrides, keyed by dotted YAML path in original key form. */
		public Map<String, Object> overrides() {
			return overrides;
		}
	}

	/**
	 * Full binding pipeline: read {@code application.yml}, extract the prefix
	 * section, normalize keys, apply {@code @DefaultValue} defaults from the
	 * {@link BindingContext}, then profile overrides, and convert to the target
	 * record type via Jackson.
	 *
	 * @param ctx
	 *            carries defaults and overrides (never null; use
	 *            {@link BindingContext#of()})
	 * @param prefix
	 *            the YAML prefix (e.g. "app", "server.tls"), or empty for root
	 * @param targetType
	 *            the record class to bind to
	 * @return the bound instance
	 */
	@SuppressWarnings("unchecked")
	public static <T> T bind(BindingContext ctx, String prefix, Class<T> targetType) {
		Map<String, Object> fieldDefaults = ctx.defaults();
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
			applyFieldDefaults(section, fieldDefaults);
			applyProfileOverrides(section, prefix, ctx.overrides());
			resolveEnvPlaceholders(section);
			return YAML_MAPPER.convertValue(section, targetType);
		} catch (ConfigurationException e) {
			throw e;
		} catch (Exception e) {
			throw new BeanCreationException("Failed to bind @ConfigurationProperties: " + targetType.getName(), e);
		}
	}

	/**
	 * Fills {@code section} with the supplied field defaults for any component that
	 * is absent (YAML values win). Reflection-free: values arrive already converted
	 * by the caller. Shared by both the runtime and AOT engines.
	 */
	private static void applyFieldDefaults(Map<String, Object> section, Map<String, Object> fieldDefaults) {
		if (fieldDefaults.isEmpty()) {
			return;
		}
		for (Map.Entry<String, Object> entry : fieldDefaults.entrySet()) {
			String name = entry.getKey();
			if (!section.containsKey(name)) {
				section.put(name, entry.getValue());
			}
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
	 * Merges the {@link BindingContext}'s profile overrides into the (already
	 * normalized) section map, just before Jackson conversion. Overrides are keyed
	 * by dotted path in the original YAML key form; each key is normalized to
	 * camelCase so it lands on the matching record component. Nested paths
	 * ({@code server.port}) descend into nested maps. Only overrides under the
	 * requested {@code prefix} apply — an override for {@code server.port} is
	 * ignored when binding the {@code app} section.
	 *
	 * @param section
	 *            normalized section map (mutated in place)
	 * @param prefix
	 *            the binding prefix, or empty for the root
	 * @param overrides
	 *            dotted-path → value, in original YAML key form
	 */
	@SuppressWarnings("unchecked")
	private static void applyProfileOverrides(Map<String, Object> section, String prefix,
			Map<String, Object> overrides) {
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

	/**
	 * Resolves {@code ${VAR}} and {@code ${VAR:-default}} placeholders in string
	 * configuration values, so configuration can be externalized (12-factor). An
	 * environment variable wins, then a system property, then the supplied default.
	 * A bare {@code ${VAR}} with no default and no value is left unchanged
	 * (graceful degradation rather than a hard failure). Applied recursively to
	 * nested sections.
	 *
	 * <p>
	 * Only strings containing the {@code ${...}} pattern are touched, so existing
	 * literal values (e.g. a JDBC URL with no placeholder) bind exactly as before.
	 * </p>
	 */
	static void resolveEnvPlaceholders(Map<String, Object> section) {
		for (Map.Entry<String, Object> entry : section.entrySet()) {
			Object value = entry.getValue();
			if (value instanceof Map<?, ?> nested) {
				resolveEnvPlaceholders((Map<String, Object>) nested);
			} else if (value instanceof String str) {
				entry.setValue(resolveString(str));
			}
		}
	}

	private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([\\w.]+)(?::(-?))?([^}]*)\\}");

	private static String resolveString(String value) {
		Matcher matcher = PLACEHOLDER.matcher(value);
		if (!matcher.find()) {
			return value;
		}
		StringBuffer sb = new StringBuffer();
		do {
			String name = matcher.group(1);
			String resolved = lookup(name);
			if (resolved == null) {
				// group(3) carries the default for ${VAR:-default} / ${VAR:default}.
				// Absent (or empty) for a bare ${VAR}, which degrades to the original
				// token rather than resolving to an empty string.
				String defaultVal = matcher.group(3);
				resolved = defaultVal != null && !defaultVal.isEmpty() ? defaultVal : matcher.group(0);
			}
			matcher.appendReplacement(sb, Matcher.quoteReplacement(resolved));
		} while (matcher.find());
		matcher.appendTail(sb);
		return sb.toString();
	}

	private static String lookup(String name) {
		String value = System.getenv(name);
		if (value != null) {
			return value;
		}
		return System.getProperty(name);
	}
}
