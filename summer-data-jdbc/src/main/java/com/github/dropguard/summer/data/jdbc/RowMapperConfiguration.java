package com.github.dropguard.summer.data.jdbc;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.ConditionalOnBean;
import com.github.dropguard.summer.core.annotation.Configuration;
import com.github.dropguard.summer.data.jdbc.query.QueryBuilder;
import org.jboss.jandex.IndexView;

/**
 * Wires {@code @RowModel} registration into the container.
 *
 * <p>Registration is split by concern so the two DI engines each do only what they should:
 *
 * <ul>
 *   <li>{@link EntityMetadataRegistrar} (engine-agnostic) registers the table/column metadata
 *       {@link QueryBuilder} needs — runs on both engines.
 *   <li>{@link ReflectiveRowMapperRegistrar} (Runtime-only, via
 *       {@code @ConditionalOnBean(RuntimeDiMarker)}) registers reflective row mappers — the AOT
 *       engine emits inline mappers instead and skips this entirely.
 * </ul>
 *
 * Both beans are conditional on a {@link JdbcTemplate} being present, so when there is no JDBC
 * usage nothing is registered.
 */
@Configuration
@ConditionalOnBean(JdbcTemplate.class)
public class RowMapperConfiguration {

    @Bean
    public EntityMetadataRegistrar entityMetadataRegistrar(
            IndexView discoveryIndex, EntityMetadataRegistry entityMetadataRegistry) {
        return new EntityMetadataRegistrar(discoveryIndex, entityMetadataRegistry);
    }

    @Bean
    public ReflectiveRowMapperRegistrar rowMapperRegistrar(
            JdbcTemplate jdbcTemplate, IndexView discoveryIndex) {
        return new ReflectiveRowMapperRegistrar(jdbcTemplate, discoveryIndex);
    }
}
