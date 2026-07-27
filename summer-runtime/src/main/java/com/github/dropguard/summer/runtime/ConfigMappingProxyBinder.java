package com.github.dropguard.summer.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dropguard.summer.core.config.ConfigBinder;
import com.github.dropguard.summer.core.config.ConfigBinder.InterfaceBinder;
import com.github.dropguard.summer.core.config.TypeConverter;
import com.github.dropguard.summer.core.config.WithDefault;
import com.github.dropguard.summer.core.config.WithName;
import com.github.dropguard.summer.core.exception.BeanCreationException;
import com.github.dropguard.summer.core.exception.ConfigurationException;
import com.github.dropguard.summer.core.exception.MissingFieldException;
import com.github.dropguard.summer.core.json.SummerObjectMapper;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

/**
 * Runtime-side binder for {@code @ConfigMapping} config interfaces.
 *
 * <p>Interface binding requires dynamic proxying, which is confined to the {@code runtime} module
 * by the {@code ReflectionConfinementTest} architecture rule — the {@code core} module (where
 * {@link ConfigBinder} lives) must stay reflection-free. The runtime engine installs this binder
 * explicitly via {@link #install()} during bootstrap (no {@link java.util.ServiceLoader}); the
 * reflective proxy machinery lives here and {@code core} never references {@code java.lang.reflect}
 * or {@code Proxy}.
 *
 * <p>The section map is produced by {@link ConfigBinder#bindSection} (shared, reflection-free);
 * this binder only resolves each abstract method to its key (method name or {@link WithName}) and
 * converts the value, recursing into nested config interfaces via child proxies.
 */
public final class ConfigMappingProxyBinder implements InterfaceBinder {

    private static final ObjectMapper YAML_MAPPER = SummerObjectMapper.createYaml();

    /**
     * Registers this binder with {@link ConfigBinder} so interface config binding works. Idempotent
     * (see {@link ConfigBinder#setInterfaceBinder}): safe to call from multiple engines/tests
     * without re-installing or clobbering an existing binder. The runtime engine calls this during
     * bootstrap; tests that bind config interfaces directly also call it.
     */
    public static void install() {
        ConfigBinder.setInterfaceBinder(new ConfigMappingProxyBinder());
    }

    @Override
    public <T> T bind(ConfigBinder.BindingContext ctx, String prefix, Class<T> targetType) {
        Map<String, Object> section = ConfigBinder.bindSection(ctx, prefix);
        @SuppressWarnings("unchecked")
        T proxy =
                (T)
                        Proxy.newProxyInstance(
                                targetType.getClassLoader(),
                                new Class<?>[] {targetType},
                                new ConfigMappingHandler(section, targetType));
        return proxy;
    }

    /**
     * {@link InvocationHandler} for a {@code @ConfigMapping} interface proxy. Resolves each
     * abstract method to its key (method name, or {@link WithName}) and returns the section value
     * converted to the method's return type.
     */
    private static final class ConfigMappingHandler implements InvocationHandler {

        private final Map<String, Object> section;
        private final Class<?> targetType;

        ConfigMappingHandler(Map<String, Object> section, Class<?> targetType) {
            this.section = section;
            this.targetType = targetType;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (args != null && args.length != 0) {
                // No config mapping method takes arguments.
                throw new UnsupportedOperationException(
                        "config mapping method must be parameterless: " + method);
            }
            switch (method.getName()) {
                case "toString":
                    return targetType.getSimpleName() + "ConfigMapping" + section;
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == (args == null ? null : args[0]);
                default:
                    break;
            }
            String key = resolveKey(method);
            Object value = section.get(key);
            if (value == null) {
                WithDefault withDefault = method.getAnnotation(WithDefault.class);
                if (withDefault != null) {
                    value = convertDefault(withDefault.value(), method.getReturnType());
                }
            }
            if (value == null) {
                throw new MissingFieldException(
                        method.getName(),
                        targetType.getSimpleName(),
                        "Missing required config key '"
                                + key
                                + "' for "
                                + targetType.getName()
                                + " (no @WithDefault and not present in YAML/overrides)");
            }
            return convertValue(value, method.getGenericReturnType());
        }

        private static String resolveKey(Method method) {
            WithName withName = method.getAnnotation(WithName.class);
            if (withName != null && !withName.value().isEmpty()) {
                return ConfigBinder.toCamelCase(withName.value());
            }
            return ConfigBinder.toCamelCase(method.getName());
        }

        private static Object convertValue(Object value, java.lang.reflect.Type type) {
            Class<?> raw =
                    type instanceof Class<?> c
                            ? c
                            : (type instanceof java.lang.reflect.ParameterizedType pt
                                    ? (Class<?>) pt.getRawType()
                                    : null);
            // A nested config mapping interface binds recursively to a child proxy,
            // never through Jackson (which cannot materialize interfaces).
            if (raw != null && raw.isInterface() && value instanceof Map<?, ?> child) {
                @SuppressWarnings("unchecked")
                Map<String, Object> childMap = (Map<String, Object>) child;
                return Proxy.newProxyInstance(
                        raw.getClassLoader(),
                        new Class<?>[] {raw},
                        new ConfigMappingHandler(childMap, raw));
            }
            try {
                return YAML_MAPPER.convertValue(
                        value, YAML_MAPPER.getTypeFactory().constructType(type));
            } catch (Exception e) {
                throw new BeanCreationException(
                        "Failed to convert config value for type " + type, e);
            }
        }

        private static Object convertDefault(String raw, Class<?> type) {
            try {
                return TypeConverter.convert(raw, type);
            } catch (ConfigurationException e) {
                // Fall back to Jackson for non-scalar defaults (rare).
                return convertValue(raw, type);
            }
        }
    }
}
