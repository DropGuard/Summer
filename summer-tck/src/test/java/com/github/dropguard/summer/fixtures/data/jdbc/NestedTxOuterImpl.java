package com.github.dropguard.summer.fixtures.data.jdbc;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import com.github.dropguard.summer.tx.Transactional;

@Component
public class NestedTxOuterImpl implements NestedTxOuter {

    private final JdbcTemplate jdbcTemplate;
    private final NestedTxInner inner;

    public NestedTxOuterImpl(JdbcTemplate jdbcTemplate, NestedTxInner inner) {
        this.jdbcTemplate = jdbcTemplate;
        this.inner = inner;
    }

    @Override
    @Transactional
    public void insertWithNested(Long id) {
        jdbcTemplate.update("INSERT INTO users (id, name) VALUES (?, ?)", id, "outer");
        inner.insert(id);
    }

    @Override
    public boolean exists(Long id) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }
}
