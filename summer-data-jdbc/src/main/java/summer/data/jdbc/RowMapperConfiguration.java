package summer.data.jdbc;

import org.jboss.jandex.IndexView;
import summer.core.annotation.Bean;
import summer.core.annotation.ConditionalOnBean;
import summer.core.annotation.Configuration;
import summer.data.jdbc.query.QueryBuilder;

/**
 * Wires {@code @RowModel} registration into the container.
 *
 * <p>
 * Registration is split by concern so the two DI engines each do only what they
 * should:
 * <ul>
 * <li>{@link EntityMetadataRegistrar} (engine-agnostic) registers the
 * table/column metadata {@link QueryBuilder} needs — runs on both engines.</li>
 * <li>{@link ReflectiveRowMapperRegistrar} (Runtime-only, via
 * {@code @ConditionalOnBean(RuntimeDiMarker)}) registers reflective row mappers
 * — the AOT engine emits inline mappers instead and skips this entirely.</li>
 * </ul>
 * Both beans are conditional on a {@link JdbcTemplate} being present, so when
 * there is no JDBC usage nothing is registered.
 * </p>
 */
@Configuration
@ConditionalOnBean(JdbcTemplate.class)
public class RowMapperConfiguration {

	@Bean
	public EntityMetadataRegistrar entityMetadataRegistrar(IndexView discoveryIndex,
			EntityMetadataRegistry entityMetadataRegistry) {
		return new EntityMetadataRegistrar(discoveryIndex, entityMetadataRegistry);
	}

	@Bean
	public ReflectiveRowMapperRegistrar rowMapperRegistrar(JdbcTemplate jdbcTemplate, IndexView discoveryIndex) {
		return new ReflectiveRowMapperRegistrar(jdbcTemplate, discoveryIndex);
	}
}
