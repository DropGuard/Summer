package summer.data.jdbc;

import java.util.HashMap;
import summer.core.exception.DataAccessException;

/**
 * Type-safe map from row class to RowMapper. Exists as a distinct type so the
 * DI framework can distinguish it from other Map beans.
 */
public class RowMapperRegistry extends HashMap<Class<?>, RowMapper<?>> {

	@SuppressWarnings("unchecked")
	public <T> RowMapper<T> get(Class<T> rowType) {
		RowMapper<?> mapper = super.get(rowType);
		if (mapper == null) {
			throw new DataAccessException("No RowMapper registered for " + rowType.getName()
					+ ". Ensure the class is annotated with @RowModel and summer-maven-plugin is configured.");
		}
		return (RowMapper<T>) mapper;
	}
}
