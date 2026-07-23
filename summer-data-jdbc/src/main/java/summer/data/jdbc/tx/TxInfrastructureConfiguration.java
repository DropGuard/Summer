package summer.data.jdbc.tx;

import javax.sql.DataSource;
import summer.core.annotation.Bean;
import summer.core.annotation.ConditionalOnBean;
import summer.core.annotation.Configuration;
import summer.tx.TransactionInterceptor;
import summer.tx.TransactionManager;

/**
 * Configuration for transaction infrastructure beans.
 *
 * <p>
 * Provides {@link SimpleJdbcTransactionManager} and
 * {@link TransactionInterceptor}. Connection sharing between
 * {@code @Transactional} and {@link summer.data.jdbc.JdbcTemplate} is handled
 * inside {@code JdbcTemplate} itself (it reuses the active transaction's
 * connection when one exists on the current thread), so users never wrap their
 * {@code DataSource} by hand.
 * </p>
 */
@Configuration
@ConditionalOnBean(DataSource.class)
public class TxInfrastructureConfiguration {

	@Bean
	public SimpleJdbcTransactionManager transactionManager(DataSource dataSource) {
		return new SimpleJdbcTransactionManager(dataSource);
	}

	@Bean
	public TransactionInterceptor transactionInterceptor(TransactionManager transactionManager) {
		return new TransactionInterceptor(transactionManager);
	}
}
