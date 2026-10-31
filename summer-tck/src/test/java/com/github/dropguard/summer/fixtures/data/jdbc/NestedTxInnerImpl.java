package com.github.dropguard.summer.fixtures.data.jdbc;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import com.github.dropguard.summer.tx.Transactional;

@Component
public class NestedTxInnerImpl implements NestedTxInner {

    private final JdbcTemplate jdbcTemplate;

    public NestedTxInnerImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void insert(Long id) {
        jdbcTemplate.update("INSERT INTO users (id, name) VALUES (?, ?)", id, "inner");
    }
}
