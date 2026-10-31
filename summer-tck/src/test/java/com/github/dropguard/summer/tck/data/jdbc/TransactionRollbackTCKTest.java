package com.github.dropguard.summer.tck.data.jdbc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import com.github.dropguard.summer.fixtures.data.jdbc.NestedTxOuter;
import com.github.dropguard.summer.fixtures.data.jdbc.TransactionalRecordRepo;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;
import com.github.dropguard.summer.tx.SummerTransactionException;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;

/**
 * Dual-engine (Runtime + AOT) contract: a {@code @Transactional} method that writes then throws
 * must roll the write back.
 *
 * <p>{@code JdbcTestConfig} supplies the H2 {@code DataSource} + {@code JdbcTemplate}; {@code
 * TxInfrastructureConfiguration} auto-registers the transaction manager and {@code
 * TransactionInterceptor} once a {@code DataSource} is present, so the proxy created for {@code
 * TransactionalRecordRepo} (it implements an interface, so JDK proxying applies) opens a real
 * transaction. The TCK asserts the insert performed inside {@code insertThenFail} is gone after the
 * thrown exception — proving the interceptor actually wraps the call in a transaction rather than
 * the method running connection-less.
 */
@SummerTest
public class TransactionRollbackTCKTest {

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp(BeanContainer context) {
        jdbcTemplate = context.getBean(JdbcTemplate.class);
        jdbcTemplate.update(
                "CREATE TABLE IF NOT EXISTS users (id INT PRIMARY KEY, name VARCHAR(255))");
        jdbcTemplate.update("TRUNCATE TABLE users");
    }

    @DualEngine
    void transactionalWriteRollsBackOnException(BeanContainer context) {
        TransactionalRecordRepo repo = context.getBean(TransactionalRecordRepo.class);
        long probeId = 9999L;

        assertThrows(
                IllegalStateException.class,
                () -> repo.insertThenFail(probeId),
                "insertThenFail must propagate its forced failure");

        assertFalse(
                repo.exists(probeId),
                "row inserted inside the failed transaction must be rolled back");
    }

    @DualEngine
    void checkedExceptionRollsBack(BeanContainer context) {
        TransactionalRecordRepo repo = context.getBean(TransactionalRecordRepo.class);
        long probeId = 8888L;

        assertThrows(IOException.class, () -> repo.insertThenFailChecked(probeId));

        // IOException is checked. Spring's default rolls back only unchecked exceptions unless
        // rollbackFor is declared; Summer's attribute-free model promises ANY thrown exception
        // rolls back — with no rollbackFor to forget.
        assertFalse(repo.exists(probeId), "a checked failure must roll back the insert");
    }

    @DualEngine
    void nestedTransactionalCallFailsLoudlyAndOuterRollsBack(BeanContainer context) {
        NestedTxOuter outer = context.getBean(NestedTxOuter.class);
        long probeId = 7777L;

        assertThrows(
                SummerTransactionException.class,
                () -> outer.insertWithNested(probeId),
                "the nested begin() through the inner bean's proxy must fail loudly");

        // The outer boundary rolls back: the row the outer transaction wrote must be gone.
        assertFalse(outer.exists(probeId), "the outer transaction must roll back");
    }
}
