package summer.core;

/**
 * Utility class for working with records in Summer.
 */
public final class RecordUtils {
	private RecordUtils() {
		// Utility class, not instantiable
	}

	/**
	 * Checks if a class is a Java record type.
	 */
	public static boolean isRecord(Class<?> clazz) {
		return clazz.isRecord();
	}
}
