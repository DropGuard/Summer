package summer.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
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

	/**
	 * Gets all the component types of a record.
	 */
	public static List<Class<?>> getRecordComponents(Class<?> recordClass) {
		if (!isRecord(recordClass)) {
			return Collections.emptyList();
		}

		return Arrays.stream(recordClass.getRecordComponents()).map(component -> component.getType())
				.collect(Collectors.toList());
	}
}