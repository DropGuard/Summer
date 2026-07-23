package summer.fixtures.data.jdbc;

/**
 * Positive fixture contract: a transactional repository that implements an
 * interface, so JDK dynamic proxying applies and {@code @Transactional} is
 * honoured. {@link TransactionalRecordRepoImpl} is the concrete bean.
 */
public interface TransactionalRecordRepo {
	void insertThenFail(Long id);

	boolean exists(Long id);
}
