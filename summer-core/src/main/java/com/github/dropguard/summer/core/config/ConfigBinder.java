package com.github.dropguard.summer.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.exception.BeanCreationException;
import com.github.dropguard.summer.core.exception.ConfigurationException;
import com.github.dropguard.summer.core.json.SummerObjectMapper;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Self-contained YAML-to-config binding for the {@code @ConfigMapping} interface model.
 *
 * <p>The full pipeline — YAML read, section extraction, key normalization,
 * {@code @WithDefault}/{@code @TestProfile} application, {@code ${ENV}} placeholder resolution, and
 * interface proxying — lives here. Both the runtime and AOT engines share this single
 * implementation; no external binder needs to be installed.
 */
@Internal
public final class ConfigBinder {

    private static final ObjectMapper YAML_MAPPER = SummerObjectMapper.createYaml();
    private static final String YAML_RESOURCE = "application.yml";

    public ConfigBinder() {}

    /**
     * Immutable carrier for the two inputs that can alter a config binding beyond the YAML itself:
     *
     * <ul>
     *   <li>{@code defaults} — pre-converted {@code @WithDefault} values, keyed by method name (for
     *       interfaces) or record component name (legacy). Supplied by the caller (runtime extracts
     *       them reflectively; AOT emits them inline via {@link
     *       com.github.dropguard.summer.core.config.TypeConverter}).
     *   <li>{@code overrides} — per-profile overrides, keyed by dotted YAML path, in the original
     *       YAML key form. Baked in at code-generation time from {@code @TestProfile} so no runtime
     *       {@code ThreadLocal} is needed.
     * </ul>
     *
     * <p>Both win over YAML-absent fields; overrides win over defaults. The AOT generator builds
     * these literals at generation time, which is also what it hashes to derive the generated
     * container's identity.
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

        /** Only profile overrides (no {@code @WithDefault} metadata). */
        public static BindingContext of(Map<String, Object> overrides) {
            return new BindingContext(Map.of(), overrides);
        }

        /** Default values and profile overrides together. */
        public static BindingContext of(
                Map<String, Object> defaults, Map<String, Object> overrides) {
            return new BindingContext(defaults, overrides);
        }

        /** Pre-converted {@code @WithDefault} values, keyed by method name. */
        public Map<String, Object> defaults() {
            return defaults;
        }

        /** Profile overrides, keyed by dotted YAML path in original key form. */
        public Map<String, Object> overrides() {
            return overrides;
        }
    }

    /**
     * Full binding pipeline: read {@code application.yml}, extract the prefix section, normalize
     * keys, apply {@code @WithDefault} defaults from the {@link BindingContext}, then profile
     * overrides, resolve {@code ${…}} placeholders, and bind to the target type.
     *
     * @param ctx carries defaults and overrides (never null; use {@link BindingContext#of()})
     * @param prefix the YAML prefix (e.g. "app", "server.tls"), or empty for root
     * @param targetType the config mapping interface or legacy record to bind to
     * @return the bound instance
     */
    @SuppressWarnings("unchecked")
    public <T> T bind(BindingContext ctx, String prefix, Class<T> targetType) {
        Map<String, Object> section = bindSection(ctx, prefix);
        try {
            return YAML_MAPPER.convertValue(section, targetType);
        } catch (ConfigurationException e) {
            throw e;
        } catch (Exception e) {
            throw new BeanCreationException("Failed to bind config: " + targetType.getName(), e);
        }
    }

    /**
     * Runs the read + section-extract + normalize + defaults + overrides + placeholder pipeline and
     * returns the resulting (normalized, fully-resolved) {@code Map}. Shared by the runtime proxy
     * binder and the AOT generator, which feeds the map into a statically-generated, strongly-typed
     * impl constructor — so the AOT runtime path performs a plain {@code Map → final fields} copy
     * with zero reflection.
     *
     * @param ctx carries defaults and overrides (never null)
     * @param prefix the YAML prefix, or empty for root
     * @return the normalized, resolved section map
     */
    public Map<String, Object> bindSection(BindingContext ctx, String prefix) {
        Map<String, Object> fieldDefaults = ctx.defaults();
        try (InputStream stream =
                Thread.currentThread().getContextClassLoader().getResourceAsStream(YAML_RESOURCE)) {
            Map<String, Object> section;
            if (stream != null) {
                Map<String, Object> root = YAML_MAPPER.readValue(stream, Map.class);
                section = prefix != null && !prefix.isEmpty() ? extractSection(root, prefix) : root;
                if (section == null) {
                    section = new HashMap<>();
                }
                section = normalizeKeys(section);
            } else {
                section = new HashMap<>();
            }
            section = applyFieldDefaults(section, fieldDefaults);
            section = applyProfileOverrides(section, prefix, ctx.overrides());
            section = resolveEnvPlaceholders(section);
            return section;
        } catch (ConfigurationException e) {
            throw e;
        } catch (Exception e) {
            throw new BeanCreationException("Failed to bind config section: " + prefix, e);
        }
    }

    /**
     * Pure merge of supplied defaults into {@code section}: any component absent from {@code
     * section} is filled from {@code fieldDefaults} (YAML values win). Returns a new map; the
     * inputs are never mutated. Reflection-free: values arrive already converted by the caller.
     * Shared by both the runtime and AOT engines.
     */
    private static Map<String, Object> applyFieldDefaults(
            Map<String, Object> section, Map<String, Object> fieldDefaults) {
        if (fieldDefaults.isEmpty()) {
            return section;
        }
        Map<String, Object> result = new HashMap<>(section);
        for (Map.Entry<String, Object> entry : fieldDefaults.entrySet()) {
            result.putIfAbsent(entry.getKey(), entry.getValue());
        }
        return result;
    }

    /**
     * Extracts a nested section from the YAML root map by splitting the prefix on dots and walking
     * each level.
     *
     * @return the section map, or null if any level is missing
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> extractSection(Map<String, Object> root, String prefix) {
        Map<String, Object> current = root;
        for (String part : prefix.split("\\.")) {
            Object value = current.get(part);
            if (!(value instanceof Map)) return null;
            current = (Map<String, Object>) value;
        }
        return current;
    }

    /**
     * Recursively converts all map keys from kebab-case/snake-case/dot-separated to camelCase so
     * they match Java record component names.
     */
    public static Map<String, Object> normalizeKeys(Map<String, Object> map) {
        Map<String, Object> result = new HashMap<>();
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

    /** Converts a single key from kebab-case, snake_case, or dot.separated to camelCase. */
    public static String toCamelCase(String key) {
        if (key == null || key.isEmpty()) return key;
        String[] parts = key.split("[-_.]");
        if (parts.length == 1) return key;
        StringBuilder sb = new StringBuilder(parts[0].toLowerCase());
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                sb.append(Character.toUpperCase(parts[i].charAt(0)))
                        .append(parts[i].substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }

    /**
     * Merges the {@link BindingContext}'s profile overrides into the (already normalized) section
     * map, just before Jackson conversion. Overrides are keyed by dotted path in the original YAML
     * key form; each key is normalized to camelCase so it lands on the matching record component.
     * Nested paths ({@code server.port}) descend into nested maps. Only overrides under the
     * requested {@code prefix} apply — an override for {@code server.port} is ignored when binding
     * the {@code app} section.
     *
     * @param section normalized section map (mutated in place)
     * @param prefix the binding prefix, or empty for the root
     * @param overrides dotted-path → value, in original YAML key form
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> applyProfileOverrides(
            Map<String, Object> section, String prefix, Map<String, Object> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return section;
        }
        String scope = (prefix == null || prefix.isEmpty()) ? "" : prefix + ".";
        Map<String, Object> result = new HashMap<>(section);
        for (Map.Entry<String, Object> entry : overrides.entrySet()) {
            String key = entry.getKey();
            if (!scope.isEmpty() && !key.startsWith(scope)) {
                continue;
            }
            String relative = scope.isEmpty() ? key : key.substring(scope.length());
            if (relative.isEmpty()) {
                continue;
            }
            result = writeNested(result, splitDotted(relative), entry.getValue());
        }
        return result;
    }

    private static String[] splitDotted(String key) {
        // Each segment is normalized to camelCase (the javadoc claimed this but it was never
        // done): an override like "tls.cert-chain" silently failed to match the camelCase field —
        // the same class of bug as an env-style key never reaching the binding.
        return java.util.Arrays.stream(key.split("\\."))
                .map(ConfigBinder::toCamelCase)
                .toArray(String[]::new);
    }

    /**
     * Walks {@code path} (already camelCased segments) into {@code target}, creating intermediate
     * {@link HashMap}s as needed, and sets the leaf to {@code value}.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> writeNested(
            Map<String, Object> target, String[] path, Object value) {
        if (path.length == 0) {
            return target;
        }
        String head = path[0];
        if (path.length == 1) {
            // Leaf: replace the value directly, without wrapping it in a container.
            Map<String, Object> copy = new HashMap<>(target);
            copy.put(head, value);
            return copy;
        }
        Map<String, Object> child =
                (target.get(head) instanceof Map<?, ?> m)
                        ? new HashMap<>((Map<String, Object>) m)
                        : new HashMap<>();
        child = writeNested(child, Arrays.copyOfRange(path, 1, path.length), value);
        Map<String, Object> copy = new HashMap<>(target);
        copy.put(head, child);
        return copy;
    }

    /**
     * Resolves {@code ${VAR}} and {@code ${VAR:-default}} placeholders in string configuration
     * values, so configuration can be externalized (12-factor). An environment variable wins, then
     * a system property, then the supplied default. A bare {@code ${VAR}} with no default and no
     * value is left unchanged (graceful degradation rather than a hard failure). Applied
     * recursively to nested sections.
     *
     * <p>Only strings containing the {@code ${...}} pattern are touched, so existing literal values
     * (e.g. a JDBC URL with no placeholder) bind exactly as before.
     */
    static Map<String, Object> resolveEnvPlaceholders(Map<String, Object> section) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : section.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                result.put(entry.getKey(), resolveEnvPlaceholders((Map<String, Object>) nested));
            } else if (value instanceof String str) {
                result.put(entry.getKey(), resolveString(str));
            } else {
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }

    private static final Pattern PLACEHOLDER =
            Pattern.compile("\\$\\{([\\w.]+)(?::(-?))?([^}]*)\\}");

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
                resolved =
                        defaultVal != null && !defaultVal.isEmpty() ? defaultVal : matcher.group(0);
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(resolved));
        } while (matcher.find());
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String lookup(String name) {
        // Spring/Quarkus convention: system properties (-D) outrank environment variables.
        String value = System.getProperty(name);
        if (value != null) {
            return value;
        }
        return System.getenv(name);
    }
}
