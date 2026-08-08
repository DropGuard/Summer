package com.github.dropguard.summer.tck.negative.fixtures.narrow.rowmodel;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.Configuration;
import com.github.dropguard.summer.data.jdbc.EntityMetadataRegistrar;
import com.github.dropguard.summer.data.jdbc.EntityMetadataRegistry;
import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import java.sql.Connection;
import javax.sql.DataSource;
import org.jboss.jandex.IndexView;

/**
 * Minimal narrow-universe provision for the metadata regression: a stub {@code DataSource} (never
 * connected — {@code JdbcTemplate} only stores it), the {@code JdbcTemplate}, the {@code
 * EntityMetadataRegistry}, and an {@code EntityMetadataRegistrar} produced directly (the
 * framework's {@code RowMapperConfiguration} is avoided on purpose — its {@code
 * ReflectiveRowMapperRegistrar} product is package-private and would break the generated AOT code
 * in a narrow universe where the class-level {@code @ConditionalOnBean(RuntimeDiMarker)} gate is
 * invisible).
 */
@Configuration
public class EntityMetadataNarrowConfig {

    @Bean
    public DataSource dataSource() {
        return new DataSource() {
            @Override
            public Connection getConnection() {
                throw new UnsupportedOperationException("stub data source");
            }

            @Override
            public Connection getConnection(String username, String password) {
                throw new UnsupportedOperationException("stub data source");
            }

            @Override
            public java.io.PrintWriter getLogWriter() {
                throw new UnsupportedOperationException("stub data source");
            }

            @Override
            public void setLogWriter(java.io.PrintWriter out) {
                throw new UnsupportedOperationException("stub data source");
            }

            @Override
            public void setLoginTimeout(int seconds) {
                throw new UnsupportedOperationException("stub data source");
            }

            @Override
            public int getLoginTimeout() {
                throw new UnsupportedOperationException("stub data source");
            }

            @Override
            public java.util.logging.Logger getParentLogger() {
                throw new UnsupportedOperationException("stub data source");
            }

            @Override
            public <T> T unwrap(Class<T> iface) {
                throw new UnsupportedOperationException("stub data source");
            }

            @Override
            public boolean isWrapperFor(Class<?> iface) {
                throw new UnsupportedOperationException("stub data source");
            }
        };
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public EntityMetadataRegistry entityMetadataRegistry() {
        return new EntityMetadataRegistry();
    }

    @Bean
    public EntityMetadataRegistrar entityMetadataRegistrar(
            IndexView discoveryIndex, EntityMetadataRegistry entityMetadataRegistry) {
        return new EntityMetadataRegistrar(discoveryIndex, entityMetadataRegistry);
    }
}
