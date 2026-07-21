package summer.data.jdbc;

import org.jboss.jandex.IndexView;

/**
 * Registers a {@link RowMapper} for every {@code @RowModel} record in the
 * deployment's discovery index.
 *
 * <p>
 * RowMapper registration is a data-module concern: the mapping between a
 * {@code @RowModel} record and its JDBC columns is defined by
 * {@link RowMapperFactory} and owned by this module. The DI engines no longer
 * reach across module boundaries to wire mappers — both the Runtime and AOT
 * engines simply discover this {@code @Component} and let it run its
 * constructor, keeping the runtime engine free of any {@code summer.data.jdbc}
 * reflection coupling.
 * </p>
 *
 * <p>
 * The registrar scans the engine's {@link IndexView} bean — the deployment's
 * discovery index, which is the exact same index the DI engines discover beans
 * from. This is the faithful Quarkus model: an extension (here, the data
 * module) reads the deployment's index rather than re-deriving its own. Because
 * it scans the deployment's discovery index, it sees the same test beans (the
 * running test class's {@code test-classes} directory, indexed on demand) that
 * the DI container sees, so a {@code @RowModel} test fixture is registered
 * identically on both engines with no synthetic-bean coupling.
 * </p>
 *
 * <p>
 * Construction runs the registration eagerly (constructor injection, no
 * lifecycle hook): every discovered model is mapped before any query executes.
 * The registrar depends on {@link JdbcTemplate}, which is itself conditional on
 * a {@code DataSource} bean, so when no {@code DataSource} is present neither
 * bean is created and no registration occurs.
 * </p>
 */
/**
 * Registers a {@link RowMapper} for every {@code @RowModel} record in the
 * deployment's discovery index. Created by {@link RowMapperConfiguration}; not
 * a {@code @Component} itself because {@link JdbcTemplate} is an
 * application-provided bean, not a framework bean.
 */
public final class RowMapperRegistrar {

	private final JdbcTemplate jdbcTemplate;

	public RowMapperRegistrar(JdbcTemplate jdbcTemplate, IndexView index) {
		this.jdbcTemplate = jdbcTemplate;
		for (RowMapperFactory.RowModelMeta meta : RowMapperFactory.scanJandex(index)) {
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
