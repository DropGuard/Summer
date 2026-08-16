package com.github.dropguard.summer.tck.invisible.fixtures.narrow.jdbc;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.Configuration;
import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import javax.sql.DataSource;

/**
 * Narrow-universe provision for the producer-init regression: the {@code @Bean} producer registers
 * its own custom mapper in its body. Regression target: the AOT engine must call this producer
 * (then apply its {@code @RowModel} bake on top), NOT replace the producer body with a baked
 * construction — a producer's own initialization must survive on both engines.
 */
@Configuration
public class ProducerInitJdbcConfig {

    @Bean
    public DataSource dataSource() {
        return new NarrowH2DataSource("jdbc:h2:mem:tck_producer_init;DB_CLOSE_DELAY=-1", "sa", "");
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        JdbcTemplate template = new JdbcTemplate(dataSource);
        template.registerMapper(
                CustomMappedType.class,
                (rs, rowNum) -> new CustomMappedType(rs.getInt("id"), rs.getString("name")));
        return template;
    }
}
