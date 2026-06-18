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
 * {@link TransactionInterceptor}. The conditional logic is handled by the bean
 * classes themselves via {@code @ConditionalOnBean}.
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
