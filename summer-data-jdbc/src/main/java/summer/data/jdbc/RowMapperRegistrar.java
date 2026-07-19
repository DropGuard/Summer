package summer.data.jdbc;

import org.jboss.jandex.IndexView;
import summer.core.bean.IndexUniverse;

/**
 * Registers a {@link RowMapper} for every {@code @RowModel} record on the
 * classpath.
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
 * The registrar reads the project's Jandex indexes itself (data-jdbc already
 * depends on Jandex) to discover {@code @RowModel} records. It does
 * <em>not</em> depend on the engine's {@link IndexView} bean —
 * {@code IndexView} is engine internal state, not a business dependency — so
 * this component is identical to wire on both engines and introduces no
 * synthetic-bean coupling.
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
 * Registers a {@link RowMapper} for every {@code @RowModel} record on the
 * classpath. Created by {@link RowMapperConfiguration}; not a
 * {@code @Component} itself because {@link JdbcTemplate} is an
 * application-provided bean, not a framework bean.
 */
public final class RowMapperRegistrar {

	private final JdbcTemplate jdbcTemplate;

	public RowMapperRegistrar(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
		IndexView index = loadProjectIndex();
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

	/**
	 * Loads the discovery universe through the framework's single index contract.
	 *
	 * <p>
	 * Uses {@link IndexUniverse#testIndexView()} so test-tree {@code @RowModel}
	 * records (which live in {@code META-INF/jandex-test.idx}) are discovered
	 * exactly the way the DI engines see them — the same beans a
	 * {@code @QuarkusTest} would wire. Reading {@code jandex.idx} alone (the
	 * previous behaviour) made test fixtures invisible to the data module and
	 * silently skipped their mappers. {@link RowMapperFactory#scanJandex} then
	 * filters for {@code @RowModel} regardless of which module owns the record.
	 * </p>
	 */
	private static IndexView loadProjectIndex() {
		return IndexUniverse.testIndexView();
	}
}
