package summer.data.jdbc;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import summer.core.exception.DataAccessException;

/**
 * Registry for pre-compiled RowMapper instances.
 *
 * <p>
 * This is a framework infrastructure bean provided by
 * {@code JdbcInfrastructureConfiguration}. RowMapper implementations are
 * registered explicitly by AOT-generated configuration classes.
 * </p>
 */
public class RowMapperRegistry {

	private final Map<Class<?>, RowMapper<?>> mappers = new ConcurrentHashMap<>();

	/**
	 * Registers a RowMapper for the given row type.
	 *
	 * @param rowType
	 *            the entity/row class
	 * @param mapper
	 *            the RowMapper implementation
	 */
	public void register(Class<?> rowType, RowMapper<?> mapper) {
		mappers.put(rowType, mapper);
	}

	/**
	 * Gets the registered RowMapper for the given row type.
	 *
	 * @throws DataAccessException
	 *             if no mapper is registered for the type
	 */
	@SuppressWarnings("unchecked")
	public <T> RowMapper<T> getMapper(Class<T> rowType) {
		RowMapper<?> mapper = mappers.get(rowType);
		if (mapper == null) {
			throw new DataAccessException(
					"No RowMapper registered for " + rowType.getName()
							+ ". Ensure the class is annotated with @RowModel and summer-maven-plugin is configured.");
		}
		return (RowMapper<T>) mapper;
	}
}
