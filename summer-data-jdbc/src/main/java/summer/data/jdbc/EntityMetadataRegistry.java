package summer.data.jdbc;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import summer.core.Component;
import summer.data.jdbc.RowMapperFactory.RowModelMeta;

/**
 * In-memory registry of {@link EntityMetadata}, populated once during container
 * assembly from the same {@code @RowModel} scan that registers RowMappers.
 *
 * <p>
 * QueryBuilder reads exclusively from this registry — it never re-scans the
 * Jandex index, so there is exactly one discovery pass per deployment. The
 * registry is keyed by entity class; {@link #register(RowModelMeta)} is called
 * by {@code ReflectiveRowMapperRegistrar} for every discovered model.
 * </p>
 */
@Component
public final class EntityMetadataRegistry {

	private final Map<String, EntityMetadata> byClassName = new LinkedHashMap<>();

	public void register(RowModelMeta meta) {
		Set<String> columns = new java.util.LinkedHashSet<>();
		for (var field : meta.fields()) {
			columns.add(RowMapperFactory.camelToSnake(field.name()));
		}
		byClassName.put(meta.modelClassName(), new EntityMetadata(meta.tableName(), columns, meta.fields()));
	}

	public EntityMetadata get(Class<?> entityClass) {
		EntityMetadata meta = byClassName.get(entityClass.getName());
		if (meta == null) {
			throw new IllegalArgumentException("Not a registered @RowModel entity: " + entityClass.getName()
					+ ". Ensure it is annotated with @RowModel(table=...) and discovered by the container.");
		}
		return meta;
	}

	public boolean contains(Class<?> entityClass) {
		return byClassName.containsKey(entityClass.getName());
	}

	public Map<String, EntityMetadata> all() {
		return Collections.unmodifiableMap(byClassName);
	}
}
