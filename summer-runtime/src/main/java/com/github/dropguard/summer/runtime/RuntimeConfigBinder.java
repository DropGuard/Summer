package com.github.dropguard.summer.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.config.ConfigBinder;
import com.github.dropguard.summer.core.config.WithDefault;
import com.github.dropguard.summer.core.config.WithName;
import com.github.dropguard.summer.core.exception.MissingFieldException;
import com.github.dropguard.summer.core.json.SummerObjectMapper;
import java.util.Map;

/**
 * Runtime config binder that binds {@code @ConfigMapping} interfaces via JDK dynamic proxies.
 *
 * <p>Wraps {@link ConfigBinder} to add interface proxy binding; the AOT engine generates typed
 * implementations from the section map at build time and never needs this class.
 */
@Internal
public final class RuntimeConfigBinder {

    private static final ObjectMapper YAML_MAPPER = SummerObjectMapper.createYaml();
    private final ConfigBinder delegate = new ConfigBinder();

    public RuntimeConfigBinder() {}

    public <T> T bind(ConfigBinder.BindingContext ctx, String prefix, Class<T> targetType) {
        if (targetType.isInterface()) {
            Map<String, Object> section = delegate.bindSection(ctx, prefix);
            return bindInterface(section, targetType);
        }
        return delegate.bind(ctx, prefix, targetType);
    }

    /**
     * Mirrors {@code ConfigImplGenerator.defaultExpr}: collection-typed defaults resolve to the
     * empty collection (the raw {@code @WithDefault} string is not coercible to a collection),
     * everything else uses the raw string and lets the YAML mapper coerce it.
     */
    private static Object withDefaultValue(
            java.lang.reflect.Method method, WithDefault withDefault) {
        Class<?> returnType = method.getReturnType();
        if (returnType == java.util.List.class || returnType == java.util.Collection.class) {
            return java.util.List.of();
        }
        if (returnType == java.util.Map.class) {
            return java.util.Map.of();
        }
        return withDefault.value();
    }

    @SuppressWarnings("unchecked")
    private <T> T bindInterface(Map<String, Object> section, Class<T> type) {
        return (T)
                java.lang.reflect.Proxy.newProxyInstance(
                        type.getClassLoader(),
                        new Class<?>[] {type},
                        (proxy, method, args) -> {
                            if (method.getDeclaringClass() == Object.class) {
                                return switch (method.getName()) {
                                    case "equals" -> proxy == args[0];
                                    case "hashCode" -> System.identityHashCode(proxy);
                                    case "toString" -> type.getSimpleName() + "Config" + section;
                                    default ->
                                            throw new UnsupportedOperationException(
                                                    method.toString());
                                };
                            }
                            // Key resolution must mirror the AOT generator
                            // (ConfigImplGenerator.resolveKey): @WithName wins, else the
                            // camelCased method name — both engines must resolve the same key.
                            WithName withName = method.getAnnotation(WithName.class);
                            String base =
                                    (withName != null && !withName.value().isEmpty())
                                            ? withName.value()
                                            : method.getName();
                            String key = ConfigBinder.toCamelCase(base);
                            Object value = section.get(key);
                            if (value == null) {
                                WithDefault withDefault = method.getAnnotation(WithDefault.class);
                                if (withDefault != null) {
                                    value = withDefaultValue(method, withDefault);
                                } else {
                                    throw new MissingFieldException(
                                            method.getName(),
                                            type.getSimpleName(),
                                            "Missing config key '"
                                                    + key
                                                    + "' for "
                                                    + type.getName());
                                }
                            }
                            Class<?> returnType = method.getReturnType();
                            if (returnType.isInterface() && value instanceof Map<?, ?> nested) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> nestedSection = (Map<String, Object>) nested;
                                return bindInterface(nestedSection, returnType);
                            }
                            return YAML_MAPPER.convertValue(
                                    value,
                                    YAML_MAPPER
                                            .getTypeFactory()
                                            .constructType(method.getGenericReturnType()));
                        });
    }
}
