package com.github.dropguard.summer.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dropguard.summer.core.exception.BeanCreationException;
import com.github.dropguard.summer.core.exception.ConfigurationException;
import com.github.dropguard.summer.core.json.SummerObjectMapper;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared YAML-to-config binding for the {@code @ConfigMapping} interface config model.
 *
 * <p>This class is reflection-free (the AOT/runtime engines and the {@code
 * ReflectionConfinementTest} architecture rule require the {@code core} module to stay
 * reflection-free). The YAML read, section extraction, key normalization,
 * {@code @WithDefault}/{@code @TestProfile} application and {@code ${ENV}} placeholder resolution
 * all live here and are shared by both engines.
 *
 * <p>Interface ({@code @ConfigMapping}) binding needs dynamic proxying, which is confined to the
 * {@code runtime} module (the only place the architecture rule permits {@code java.lang.reflect}).
 * The runtime engine registers an {@link InterfaceBinder} via {@link ServiceLoader}; {@link #bind}
 * delegates interface targets to it, so {@code core} itself never touches {@code Proxy} or {@link
 * java.lang.reflect.Method}. The legacy record path uses Jackson {@code convertValue} and is
 * removed once the migration to interfaces completes.
 */
public final class ConfigBinder {

    private static final ObjectMapper YAML_MAPPER = SummerObjectMapper.createYaml();
    private static final String YAML_RESOURCE = "application.yml";

    private ConfigBinder() {}

    /**
     * Immutable carrier for the two inputs that can alter a config binding beyond the YAML itself:
     *
     * <ul>
     *   <li>{@code defaults} — pre-converted {@code @WithDefault} values, keyed by method name (for
     *       interfaces) or record component name (legacy). Supplied by the caller (runtime extracts
     *       them reflectively; AOT emits them inline via {@link
     *       com.github.dropguard.summer.core.util.TypeConverter}).
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
     * overrides, and bind to the target type.
     *
     * <p>Interface targets are bound through a runtime-supplied proxy (see {@link
     * InterfaceBinder}); legacy record targets through Jackson {@code convertValue} (migration
     * window only).
     *
     * @param ctx carries defaults and overrides (never null; use {@link BindingContext#of()})
     * @param prefix the YAML prefix (e.g. "app", "server.tls"), or empty for root
     * @param targetType the config mapping interface (or legacy record) to bind to
     * @return the bound instance
     */
    @SuppressWarnings("unchecked")
    public static <T> T bind(BindingContext ctx, String prefix, Class<T> targetType) {
        if (targetType.isInterface()) {
            // Interface binding is confined to the runtime module (reflection rule). The runtime
            // engine installs the actual binder via {@link #setInterfaceBinder} at startup, so this
            // method stays reflection-free.
            if (interfaceBinder == null) {
                throw new BeanCreationException(
                        "No InterfaceBinder installed (is the summer-runtime module active?)."
                                + " Cannot bind @ConfigMapping interface.");
            }
            return interfaceBinder.bind(ctx, prefix, targetType);
        }
        // Legacy record path — retained only during the migration window; removed
        // once every config holder has moved to the @ConfigMapping interface model.
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
     * Installs the runtime-supplied binder for {@code @ConfigMapping} interfaces. Called by the
     * {@code runtime} module (the only place permitted to use {@code java.lang.reflect}) during
     * engine bootstrap; never invoked by {@code core} itself.
     */
    /**
     * Installs the runtime-supplied binder for {@code @ConfigMapping} interfaces. Idempotent: a
     * second install is ignored so multiple engines/tests can call it without clobbering the first.
     * Called by the {@code runtime} module (the only place permitted to use {@code
     * java.lang.reflect}) during engine bootstrap; never invoked by {@code core} itself.
     */
    public static void setInterfaceBinder(InterfaceBinder binder) {
        if (interfaceBinder != null) {
            return;
        }
        interfaceBinder = binder;
    }

    private static volatile InterfaceBinder interfaceBinder;

    /**
     * Binds a {@code @ConfigMapping} interface to its resolved section map. Implemented by the
     * {@code runtime} module (the only place permitted to use {@code java.lang.reflect}); never
     * referenced reflectively by {@code core} itself.
     */
    @FunctionalInterface
    public interface InterfaceBinder {
        <T> T bind(BindingContext ctx, String prefix, Class<T> targetType);
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
    public static Map<String, Object> bindSection(BindingContext ctx, String prefix) {
        Map<String, Object> fieldDefaults = ctx.defaults();
        try (InputStream stream =
                Thread.currentThread().getContextClassLoader().getResourceAsStream(YAML_RESOURCE)) {
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
        Map<String, Object> result = new LinkedHashMap<>(section);
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
        Map<String, Object> result = new LinkedHashMap<>(section);
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
        return key.split("\\.");
    }

    /**
     * Walks {@code path} (already camelCased segments) into {@code target}, creating intermediate
     * {@link LinkedHashMap}s as needed, and sets the leaf to {@code value}.
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
            Map<String, Object> copy = new LinkedHashMap<>(target);
            copy.put(head, value);
            return copy;
        }
        Map<String, Object> child =
                (target.get(head) instanceof Map<?, ?> m)
                        ? new LinkedHashMap<>((Map<String, Object>) m)
                        : new LinkedHashMap<>();
        child = writeNested(child, Arrays.copyOfRange(path, 1, path.length), value);
        Map<String, Object> copy = new LinkedHashMap<>(target);
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
        Map<String, Object> result = new LinkedHashMap<>();
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
        String value = System.getenv(name);
        if (value != null) {
            return value;
        }
        return System.getProperty(name);
    }
}
