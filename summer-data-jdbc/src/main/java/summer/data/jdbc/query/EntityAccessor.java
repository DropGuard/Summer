package summer.data.jdbc.query;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import summer.data.jdbc.EntityMetadata;
import summer.data.jdbc.RowMapperFactory.FieldMeta;

/**
 * Reads an entity's field values for mutation statements (INSERT/UPDATE).
 *
 * <p>
 * This is the write-side counterpart of the reflective {@code RowMapper}: both
 * recover entity state through runtime reflection because
 * {@code summer-aot-engine} does not yet generate {@code @RowModel} accessors.
 * Keeping the reflection here (rather than inside the builders) means a future
 * AOT accessor can replace this single class without changing the public
 * {@code QueryTemplate} API.
 * </p>
 */
final class EntityAccessor {

	private EntityAccessor() {
	}

	/**
	 * Returns an ordered map from snake_case column name to the entity's field
	 * value, following the same field-to-column rule the RowMapper uses.
	 */
	static Map<String, Object> columnValues(EntityMetadata metadata, Object entity) {
		Map<String, Object> values = new LinkedHashMap<>();
		Class<?> type = entity.getClass();
		for (FieldMeta field : metadata.fields()) {
			String column = summer.data.jdbc.RowMapperFactory.camelToSnake(field.name());
			values.put(column, readField(type, field.name(), entity));
		}
		return values;
	}

	private static Object readField(Class<?> type, String fieldName, Object entity) {
		try {
			Method getter = type.getMethod(fieldName);
			getter.setAccessible(true);
			return getter.invoke(entity);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(
					"Cannot read field '" + fieldName + "' from " + type.getName()
							+ ". @RowModel entities must expose a record component or getter for every mapped field.",
					e);
		}
	}
}
