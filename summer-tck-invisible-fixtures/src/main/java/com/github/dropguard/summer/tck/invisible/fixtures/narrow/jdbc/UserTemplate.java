package com.github.dropguard.summer.tck.invisible.fixtures.narrow.jdbc;

import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import javax.sql.DataSource;

/**
 * {@link JdbcTemplate} subclass product for the subclass regression: the AOT {@code
 * AotProductConstructor} resolution must walk the supertype chain so a subclass product inherits
 * its base type's provider (the {@code @RowModel} bake) — mirroring the runtime engine, whose
 * assignability-based lookup already covers subclasses.
 */
public class UserTemplate extends JdbcTemplate {

    public UserTemplate(DataSource dataSource) {
        super(dataSource);
    }
}
