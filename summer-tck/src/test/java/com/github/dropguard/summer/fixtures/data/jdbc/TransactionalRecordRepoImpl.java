package com.github.dropguard.summer.fixtures.data.jdbc;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import com.github.dropguard.summer.tx.Transactional;

/**
 * Concrete {@link TransactionalRecordRepo} bean.
 *
 * <p>
 * {@link #insertThenFail(Long)} demonstrates the contract the rollback TCK
 * verifies: it writes a row inside a transaction and then throws, so the
 * framework must roll the insert back. The interface (implemented here) is what
 * makes the JDK proxy — and therefore the transaction boundary — possible in
 * Summer's AOP model.
 * </p>
 */
@Component
public class TransactionalRecordRepoImpl implements TransactionalRecordRepo {

	private final JdbcTemplate jdbcTemplate;

	public TransactionalRecordRepoImpl(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	@Transactional
	public void insertThenFail(Long id) {
		jdbcTemplate.update("INSERT INTO users (id, name) VALUES (?, ?)", id, "transient");
		throw new IllegalStateException("forced failure to trigger rollback");
	}

	@Override
	public boolean exists(Long id) {
		Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, id);
		return count != null && count > 0;
	}
}
