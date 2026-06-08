package summer.core.config;

import summer.core.ErrorCode;
import summer.core.exception.ConfigurationException;

/**
 * Shared string-to-type conversion for configuration binding and parameter
 * resolution. Supports boxed types only (Integer, Long, Boolean, Double,
 * String).
 */
public final class TypeConverter {

	private TypeConverter() {
	}

	/**
	 * Converts a string value to the target boxed type.
	 *
	 * @param value
	 *            the string value to convert (null returns null)
	 * @param targetType
	 *            the target type (Integer, Long, Boolean, Double, or String)
	 * @return the converted value, or null if value is null
	 * @throws ConfigurationException
	 *             if the type is unsupported
	 */
	public static Object convert(String value, Class<?> targetType) {
		if (value == null) {
			return null;
		}
		if (targetType == String.class)
			return value;
		if (targetType == Integer.class)
			return Integer.parseInt(value);
		if (targetType == Long.class)
			return Long.parseLong(value);
		if (targetType == Boolean.class)
			return Boolean.parseBoolean(value);
		if (targetType == Double.class)
			return Double.parseDouble(value);
		throw new ConfigurationException(ErrorCode.CONFIG_PARSE_ERROR,
				"Unsupported type for conversion: " + targetType.getName());
	}
}
