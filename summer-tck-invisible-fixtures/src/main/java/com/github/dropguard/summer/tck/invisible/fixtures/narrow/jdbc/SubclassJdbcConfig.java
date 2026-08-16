package com.github.dropguard.summer.tck.invisible.fixtures.narrow.jdbc;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.Configuration;
import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import javax.sql.DataSource;

/**
 * Narrow-universe provision for the subclass regression: a {@code @Bean} product whose declared
 * return type is the {@link JdbcTemplate} subclass {@link UserTemplate} — the AOT {@code
 * AotProductConstructor} supertype-chain resolution must still apply the base type's
 * {@code @RowModel} bake.
 */
@Configuration
public class SubclassJdbcConfig {

    @Bean
    public DataSource dataSource() {
        return new NarrowH2DataSource("jdbc:h2:mem:tck_subclass;DB_CLOSE_DELAY=-1", "sa", "");
    }

    @Bean
    public UserTemplate userTemplate(DataSource dataSource) {
        return new UserTemplate(dataSource);
    }
}
