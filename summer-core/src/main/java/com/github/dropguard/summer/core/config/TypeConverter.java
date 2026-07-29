mport com.github.dropguard.summer.core.Internal;
package com.github.dropguard.summer.core.config;

mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.ErrorCode;
mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.exception.ConfigurationException;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
/**
mport com.github.dropguard.summer.core.Internal;
 * Shared value-to-type coercion for configuration binding and parameter resolution.
@Internal
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <p>One entry point — {@link #convert(Object, Class)} — accepts either a {@link String} (as parsed
mport com.github.dropguard.summer.core.Internal;
 * from YAML/default literals) or an already-resolved {@link Number} (the common case when a config
mport com.github.dropguard.summer.core.Internal;
 * section is read back from resolved properties). This matters for the AOT config binder, which
mport com.github.dropguard.summer.core.Internal;
 * receives section values as {@code Number}s and must coerce them to the declared boxed type
mport com.github.dropguard.summer.core.Internal;
 * instead of emitting a bare primitive cast that throws {@code ClassCastException}.
mport com.github.dropguard.summer.core.Internal;
 */
mport com.github.dropguard.summer.core.Internal;
public final class TypeConverter {
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private TypeConverter() {}
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * Coerces {@code value} to {@code targetType} (boxed Integer, Long, Boolean, Double, String, or
mport com.github.dropguard.summer.core.Internal;
     * an enum). A {@link String} input is parsed; a {@link Number} input is widened/narrowed by its
mport com.github.dropguard.summer.core.Internal;
     * numeric value; an {@link Enum} input is returned as-is when its type matches.
mport com.github.dropguard.summer.core.Internal;
     *
mport com.github.dropguard.summer.core.Internal;
     * @param value the value to convert (null returns null)
mport com.github.dropguard.summer.core.Internal;
     * @param targetType the target boxed type
mport com.github.dropguard.summer.core.Internal;
     * @return the converted value, or null if value is null
mport com.github.dropguard.summer.core.Internal;
     * @throws ConfigurationException if the value cannot be coerced to the target type
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    public static Object convert(Object value, Class<?> targetType) {
mport com.github.dropguard.summer.core.Internal;
        if (value == null) {
mport com.github.dropguard.summer.core.Internal;
            return null;
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        if (targetType == String.class) {
mport com.github.dropguard.summer.core.Internal;
            return value.toString();
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        if (targetType == Boolean.class) {
mport com.github.dropguard.summer.core.Internal;
            return value instanceof Boolean b ? b : Boolean.parseBoolean(value.toString().trim());
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        if (targetType.isEnum()) {
mport com.github.dropguard.summer.core.Internal;
            if (value instanceof Enum) {
mport com.github.dropguard.summer.core.Internal;
                return value;
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
            return Enum.valueOf(
mport com.github.dropguard.summer.core.Internal;
                    (Class<Enum>) targetType,
mport com.github.dropguard.summer.core.Internal;
                    value.toString().trim().toUpperCase(java.util.Locale.ROOT));
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        if (value instanceof Number n) {
mport com.github.dropguard.summer.core.Internal;
            if (targetType == Integer.class) return n.intValue();
mport com.github.dropguard.summer.core.Internal;
            if (targetType == Long.class) return n.longValue();
mport com.github.dropguard.summer.core.Internal;
            if (targetType == Double.class) return n.doubleValue();
mport com.github.dropguard.summer.core.Internal;
            throw new ConfigurationException(
mport com.github.dropguard.summer.core.Internal;
                    ErrorCode.CONFIG_PARSE_ERROR,
mport com.github.dropguard.summer.core.Internal;
                    "Unsupported numeric conversion: " + targetType.getName());
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        if (value instanceof String s) {
mport com.github.dropguard.summer.core.Internal;
            if (targetType == Integer.class) return Integer.parseInt(s.trim());
mport com.github.dropguard.summer.core.Internal;
            if (targetType == Long.class) return Long.parseLong(s.trim());
mport com.github.dropguard.summer.core.Internal;
            if (targetType == Double.class) return Double.parseDouble(s.trim());
mport com.github.dropguard.summer.core.Internal;
            throw new ConfigurationException(
mport com.github.dropguard.summer.core.Internal;
                    ErrorCode.CONFIG_PARSE_ERROR,
mport com.github.dropguard.summer.core.Internal;
                    "Unsupported type for conversion: " + targetType.getName());
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        throw new ConfigurationException(
mport com.github.dropguard.summer.core.Internal;
                ErrorCode.CONFIG_PARSE_ERROR,
mport com.github.dropguard.summer.core.Internal;
                "Unsupported conversion: "
mport com.github.dropguard.summer.core.Internal;
                        + value.getClass().getName()
mport com.github.dropguard.summer.core.Internal;
                        + " -> "
mport com.github.dropguard.summer.core.Internal;
                        + targetType.getName());
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;
}
