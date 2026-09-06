package com.github.dropguard.summer.core.config;

import com.github.dropguard.summer.core.ErrorCode;
import com.github.dropguard.summer.core.Internal;
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
@Internal
public final class TypeConverter {

    private TypeConverter() {}

    /**
     * Coerces {@code value} to {@code targetType} (boxed Integer, Long, Boolean, Double, Float,
     * Short, Byte, Character, String, or an enum). A {@link String} input is parsed; a {@link
     * Number} input is widened/narrowed by its numeric value; an {@link Enum} input is returned
     * as-is when its type matches. This is the single conversion truth shared by the runtime param
     * resolvers, the AOT generated adapter, and config binding — the runtime and AOT engines must
     * convert request parameters identically.
     *
     * @param value the value to convert (null returns null)
     * @param targetType the target boxed type
     * @return the converted value, or null if value is null
     * @throws ConfigurationException if the value cannot be coerced to the target type
     */
    @SuppressWarnings("unchecked") // targetType.isEnum() checked above; the cast is safe
    public static Object convert(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        if (targetType == String.class) {
            return value.toString();
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            if (value instanceof Boolean b) {
                return b;
            }
            // Strict: 'yes'/'1'/typo'd values silently binding to false is a config mistake —
            // they now fail loudly instead of masquerading as an explicit 'false'.
            String s = value.toString().trim();
            if (s.equalsIgnoreCase("true")) {
                return true;
            }
            if (s.equalsIgnoreCase("false")) {
                return false;
            }
            throw new ConfigurationException(
                    ErrorCode.CONFIG_PARSE_ERROR,
                    "Cannot convert '" + s + "' to boolean (expected true or false)");
        }
        if (targetType == char.class || targetType == Character.class) {
            if (value instanceof Character c) {
                return c;
            }
            String s = value.toString();
            if (s.isEmpty()) {
                throw new ConfigurationException(
                        ErrorCode.CONFIG_PARSE_ERROR,
                        "Cannot convert empty value to char for " + targetType.getName());
            }
            return s.charAt(0);
        }
        if (targetType.isEnum()) {
            if (value instanceof Enum) {
                return value;
            }
            return enumValue((Class<Enum>) targetType, value.toString().trim());
        }
        if (value instanceof Number n) {
            // Range-checked narrowing: a silent intValue()/shortValue() WRAPS out-of-range
            // values (99999999999L -> 1409286143) and a silent double->int truncates fractions —
            // both surface far from the config mistake as unrelated failures. Out-of-range or
            // lossy narrows are loud config errors instead.
            if (targetType == int.class || targetType == Integer.class)
                return checkedNumber(n, Integer.MIN_VALUE, Integer.MAX_VALUE, targetType)
                        .intValue();
            if (targetType == long.class || targetType == Long.class)
                return checkedNumber(n, Long.MIN_VALUE, Long.MAX_VALUE, targetType).longValue();
            if (targetType == double.class || targetType == Double.class) return n.doubleValue();
            if (targetType == float.class || targetType == Float.class) {
                double d = n.doubleValue();
                float f = (float) d;
                if (Float.isInfinite(f) && !Double.isInfinite(d)) {
                    throw new ConfigurationException(
                            ErrorCode.CONFIG_PARSE_ERROR,
                            "Value " + n + " overflows float for " + targetType.getName());
                }
                return f;
            }
            if (targetType == short.class || targetType == Short.class)
                return checkedNumber(n, Short.MIN_VALUE, Short.MAX_VALUE, targetType).shortValue();
            if (targetType == byte.class || targetType == Byte.class)
                return checkedNumber(n, Byte.MIN_VALUE, Byte.MAX_VALUE, targetType).byteValue();
            throw new ConfigurationException(
                    ErrorCode.CONFIG_PARSE_ERROR,
                    "Unsupported numeric conversion: " + targetType.getName());
        }
        if (value instanceof String s) {
            if (targetType == int.class || targetType == Integer.class)
                return Integer.parseInt(s.trim());
            if (targetType == long.class || targetType == Long.class)
                return Long.parseLong(s.trim());
            if (targetType == double.class || targetType == Double.class)
                return Double.parseDouble(s.trim());
            if (targetType == float.class || targetType == Float.class)
                return Float.parseFloat(s.trim());
            if (targetType == short.class || targetType == Short.class)
                return Short.parseShort(s.trim());
            if (targetType == byte.class || targetType == Byte.class)
                return Byte.parseByte(s.trim());
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

    /**
     * Case-insensitive enum lookup — the config-binding contract (Jackson's {@code
     * ACCEPT_CASE_INSENSITIVE_ENUMS} on the runtime side, the generated {@code enumValue<Type>}
     * helper's {@code equalsIgnoreCase} on the AOT side). {@code ?env=production} must bind like
     * {@code env: production}; {@code Enum.valueOf(...toUpperCase())} assumed all-uppercase
     * constants and threw on mixed-case ones.
     */
    private static Enum<?> enumValue(Class<Enum> enumType, String raw) {
        for (Object constant : enumType.getEnumConstants()) {
            if (((Enum<?>) constant).name().equalsIgnoreCase(raw)) {
                return (Enum<?>) constant;
            }
        }
        throw new ConfigurationException(
                ErrorCode.CONFIG_PARSE_ERROR,
                "No enum constant " + enumType.getName() + " matching: '" + raw + "'");
    }

    /**
     * Guards a numeric narrow to {@code [min, max]} and rejects fractional inputs (a double
     * narrowing to an integral type must not silently truncate). Returns the input unchanged when
     * it already fits.
     */
    private static Number checkedNumber(Number n, long min, long max, Class<?> targetType) {
        if (n instanceof Double || n instanceof Float) {
            double d = n.doubleValue();
            if (d != Math.rint(d) || d < min || d > max) {
                throw new ConfigurationException(
                        ErrorCode.CONFIG_PARSE_ERROR,
                        "Value "
                                + n
                                + " is not representable as "
                                + targetType.getName()
                                + " (out of range or fractional)");
            }
            return (long) d;
        }
        long v = n.longValue();
        if (v < min || v > max) {
            throw new ConfigurationException(
                    ErrorCode.CONFIG_PARSE_ERROR,
                    "Value " + n + " out of range for " + targetType.getName());
        }
        return n;
    }
}
