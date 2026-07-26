package com.github.dropguard.summer.tck.data.jdbc;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;

/**
 * JdbcTemplate CRUD behaviour TCK.
 *
 * <p>
 * The {@link com.github.dropguard.summer.data.jdbc.JdbcTemplate} is resolved
 * from the DI container (wired by
 * {@code com.github.dropguard.summer.fixtures.data.jdbc.JdbcTestConfig}), so
 * both engines assemble it through their own path and {@code @RowModel} mappers
 * are registered by {@code ReflectiveRowMapperRegistrar}. Every case runs on
 * Runtime and AOT via {@link DualEngine} — the framework-enforced parity
 * guarantee. The previous {@code RuntimeJdbcTemplateTest} /
 * {@code AotJdbcTemplateTest} siblings bypassed the container with a
 * manually-constructed {@code JdbcTemplate} whose bodies were byte-identical,
 * so they were a decoy with no real engine differentiation.
 * </p>
 */
@SummerTest
public class JdbcTemplateBehaviorTest extends AbstractJdbcTemplateTCK {

	public JdbcTemplateBehaviorTest(BeanContainer context) {
		super(context);
	}

	@DualEngine
	void insertAndQueryForList() {
		super.testInsertAndQueryForList();
	}

	@DualEngine
	void queryForListEmpty() {
		super.testQueryForListEmpty();
	}

	@DualEngine
	void queryForListWithWhereClause() {
		super.testQueryForListWithWhereClause();
	}

	@DualEngine
	void queryForObject() {
		super.testQueryForObject();
	}

	@DualEngine
	void queryForObjectNotFound() {
		super.testQueryForObjectNotFound();
	}

	@DualEngine
	void queryForObjectThrowsOnMultipleRows() {
		super.testQueryForObjectThrowsOnMultipleRows();
	}

	@DualEngine
	void update() {
		super.testUpdate();
	}

	@DualEngine
	void updateNoMatchingRows() {
		super.testUpdateNoMatchingRows();
	}

	@DualEngine
	void delete() {
		super.testDelete();
	}

	@DualEngine
	void deleteNoMatchingRows() {
		super.testDeleteNoMatchingRows();
	}

	@DualEngine
	void batchUpdate() {
		super.testBatchUpdate();
	}

	@DualEngine
	void batchUpdateEmpty() {
		super.testBatchUpdateEmpty();
	}

	@DualEngine
	void nullColumnValue() {
		super.testNullColumnValue();
	}

	@DualEngine
	void invalidSqlThrows() {
		super.testInvalidSqlThrows();
	}

	@DualEngine
	void duplicateKeyThrows() {
		super.testDuplicateKeyThrows();
	}

	@DualEngine
	void missingRowMapperThrows() {
		super.testMissingRowMapperThrows();
	}
}
