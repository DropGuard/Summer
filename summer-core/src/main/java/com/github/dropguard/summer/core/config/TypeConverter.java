package com.github.dropguard.summer.core.config;

import com.github.dropguard.summer.core.ErrorCode;
import com.github.dropguard.summer.core.exception.ConfigurationException;

/**
 * Shared value-to-type coercion for configuration binding and parameter resolution.
 *
 * <p>One entry point — {@link #convert(Object, Class)} — accepts either a {@link String} (as parsed
 * from YAML/default literals) or an already-resolved {@link Number} (the common case when a config
 * section is read back from resolved properties). This matters for the AOT config binder, which
 * receives section values as {@code Number}s and must coerce them to the declared boxed type
 * instead of emitting a bare primitive cast that throws {@code ClassCastException}.
 */
public final class TypeConverter {

    private TypeConverter() {}

    /**
     * Coerces {@code value} to {@code targetType} (boxed Integer, Long, Boolean, Double, String, or
     * an enum). A {@link String} input is parsed; a {@link Number} input is widened/narrowed by its
     * numeric value; an {@link Enum} input is returned as-is when its type matches.
     *
     * @param value the value to convert (null returns null)
     * @param targetType the target boxed type
     * @return the converted value, or null if value is null
     * @throws ConfigurationException if the value cannot be coerced to the target type
     */
    public static Object convert(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        if (targetType == String.class) {
            return value.toString();
        }
        if (targetType == Boolean.class) {
            return value instanceof Boolean b ? b : Boolean.parseBoolean(value.toString().trim());
        }
        if (targetType.isEnum()) {
            if (value instanceof Enum) {
                return value;
            }
            return Enum.valueOf(
                    (Class<Enum>) targetType,
                    value.toString().trim().toUpperCase(java.util.Locale.ROOT));
        }
        if (value instanceof Number n) {
            if (targetType == Integer.class) return n.intValue();
            if (targetType == Long.class) return n.longValue();
            if (targetType == Double.class) return n.doubleValue();
            throw new ConfigurationException(
                    ErrorCode.CONFIG_PARSE_ERROR,
                    "Unsupported numeric conversion: " + targetType.getName());
        }
        if (value instanceof String s) {
            if (targetType == Integer.class) return Integer.parseInt(s.trim());
            if (targetType == Long.class) return Long.parseLong(s.trim());
            if (targetType == Double.class) return Double.parseDouble(s.trim());
            throw new ConfigurationException(
                    ErrorCode.CONFIG_PARSE_ERROR,
                    "Unsupported type for conversion: " + targetType.getName());
        }
        throw new ConfigurationException(
                ErrorCode.CONFIG_PARSE_ERROR,
                "Unsupported conversion: "
                        + value.getClass().getName()
                        + " -> "
                        + targetType.getName());
    }
}
