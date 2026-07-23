package summer.data.jdbc;

import org.jboss.jandex.IndexView;
import summer.core.RuntimeDiMarker;
import summer.core.annotation.ConditionalOnBean;
import summer.data.jdbc.query.QueryBuilder;

/**
 * Registers reflective {@link RowMapper}s for every {@code @RowModel} record in
 * the deployment index — the Runtime engine's row-mapping path.
 *
 * <p>
 * This registrar is <b>Runtime-only</b>: the AOT engine emits inline mappers at
 * build time instead (see {@code WireMethodGenerator}), so it must not also run
 * reflective registration or the same mapper would be registered twice. The
 * engine-agnostic structural metadata (table/column mapping for
 * {@link QueryBuilder}) lives in {@link EntityMetadataRegistrar}, which runs on
 * both engines.
 * </p>
 */
@ConditionalOnBean(RuntimeDiMarker.class)
public final class ReflectiveRowMapperRegistrar {

	private final JdbcTemplate jdbcTemplate;

	public ReflectiveRowMapperRegistrar(JdbcTemplate jdbcTemplate, IndexView discoveryIndex) {
		this.jdbcTemplate = jdbcTemplate;
		for (RowMapperFactory.RowModelMeta meta : RowMapperFactory.scanJandex(discoveryIndex)) {
			jdbcTemplate.registerMapper(modelClass(meta), RowMapperFactory.createReflective(meta));
		}
	}

	private static Class<?> modelClass(RowMapperFactory.RowModelMeta meta) {
		try {
			return Class.forName(meta.modelClassName());
		} catch (ClassNotFoundException e) {
			throw new IllegalStateException("Cannot load @RowModel class: " + meta.modelClassName(), e);
		}
	}
}
