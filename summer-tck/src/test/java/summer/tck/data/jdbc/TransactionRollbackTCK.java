package summer.tck.data.jdbc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import summer.core.BeanContainer;
import summer.data.jdbc.JdbcTemplate;
import summer.fixtures.data.jdbc.TransactionalRecordRepo;
import summer.test.annotation.DualEngine;
import summer.test.annotation.SummerTest;

/**
 * Dual-engine (Runtime + AOT) contract: a {@code @Transactional} method that
 * writes then throws must roll the write back.
 *
 * <p>
 * {@code JdbcTestConfig} supplies the H2 {@code DataSource} +
 * {@code JdbcTemplate}; {@code TxInfrastructureConfiguration} auto-registers
 * the transaction manager and {@code TransactionInterceptor} once a
 * {@code DataSource} is present, so the proxy created for
 * {@code TransactionalRecordRepo} (it implements an interface, so JDK proxying
 * applies) opens a real transaction. The TCK asserts the insert performed
 * inside {@code insertThenFail} is gone after the thrown exception — proving
 * the interceptor actually wraps the call in a transaction rather than the
 * method running connection-less.
 * </p>
 */
@SummerTest
public class TransactionRollbackTCK {

	private final BeanContainer context;
	private JdbcTemplate jdbcTemplate;

	public TransactionRollbackTCK(BeanContainer context) {
		this.context = context;
	}

	@BeforeEach
	void setUp() {
		jdbcTemplate = context.getBean(JdbcTemplate.class);
		jdbcTemplate.update("CREATE TABLE IF NOT EXISTS users (id INT PRIMARY KEY, name VARCHAR(255))");
		jdbcTemplate.update("TRUNCATE TABLE users");
	}

	@DualEngine
	void transactionalWriteRollsBackOnException() {
		TransactionalRecordRepo repo = context.getBean(TransactionalRecordRepo.class);
		long probeId = 9999L;

		assertThrows(IllegalStateException.class, () -> repo.insertThenFail(probeId),
				"insertThenFail must propagate its forced failure");

		assertFalse(repo.exists(probeId), "row inserted inside the failed transaction must be rolled back");
	}
}
