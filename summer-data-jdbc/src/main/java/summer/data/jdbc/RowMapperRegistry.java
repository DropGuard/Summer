package summer.data.jdbc;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import summer.core.exception.DataAccessException;

/**
 * Registry for pre-compiled RowMapper instances.
 */
public class RowMapperRegistry {

	private static final Map<Class<?>, RowMapper<?>> mappers = new ConcurrentHashMap<>();

	/**
	 * Gets a cached RowMapper or attempts to instantiate the generated one.
	 */
	@SuppressWarnings("unchecked")
	public static <T> RowMapper<T> getMapper(Class<T> rowType) {
		return (RowMapper<T>) mappers.computeIfAbsent(rowType, type -> {
			try {
				// By convention, summer-compiler generates the RowMapper in the same package
				// with the name <EntityName>_RowMapper
				String mapperClassName = type.getName() + "_RowMapper";
				Class<?> mapperClass = Class.forName(mapperClassName);
				return (RowMapper<?>) mapperClass.getDeclaredConstructor().newInstance();
			} catch (Exception e) {
				throw new DataAccessException(
						"Could not find or instantiate generated RowMapper for " + type.getName()
								+ ". Ensure the class is annotated with @RowModel and compiled with summer-compiler.",
						e);
			}
		});
	}

}
