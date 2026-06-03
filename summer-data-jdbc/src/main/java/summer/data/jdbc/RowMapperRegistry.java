package summer.data.jdbc;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import summer.core.Component;
import summer.core.exception.DataAccessException;
import summer.core.reflect.ClassInstantiator;

/**
 * Registry for pre-compiled RowMapper instances.
 */
@Component
public class RowMapperRegistry {

	private final ClassInstantiator instantiator;
	private final Map<Class<?>, RowMapper<?>> mappers = new ConcurrentHashMap<>();

	public RowMapperRegistry(ClassInstantiator instantiator) {
		this.instantiator = instantiator;
	}

	/**
	 * Gets a cached RowMapper or attempts to instantiate the generated one.
	 */
	@SuppressWarnings("unchecked")
	public <T> RowMapper<T> getMapper(Class<T> rowType) {
		return (RowMapper<T>) mappers.computeIfAbsent(rowType, type -> {
			try {
				// By convention, summer-compiler generates the RowMapper in the same package
				// with the name <EntityName>_RowMapper
				String mapperClassName = type.getName() + "_RowMapper";
				return (RowMapper<?>) instantiator.instantiate(mapperClassName);
			} catch (Exception e) {
				throw new DataAccessException(
						"Could not find or instantiate generated RowMapper for " + type.getName()
								+ ". Ensure the class is annotated with @RowModel and compiled with summer-compiler.",
						e);
			}
		});
	}

}
