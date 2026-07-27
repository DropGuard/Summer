package com.github.dropguard.summer.data.jdbc.tx;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.ConditionalOnBean;
import com.github.dropguard.summer.core.annotation.Configuration;
import com.github.dropguard.summer.tx.TransactionInterceptor;
import com.github.dropguard.summer.tx.TransactionManager;
import javax.sql.DataSource;

/**
 * Configuration for transaction infrastructure beans.
 *
 * <p>Provides {@link SimpleJdbcTransactionManager} and {@link TransactionInterceptor}. Connection
 * sharing between {@code @Transactional} and {@link
 * com.github.dropguard.summer.data.jdbc.JdbcTemplate} is handled inside {@code JdbcTemplate} itself
 * (it reuses the active transaction's connection when one exists on the current thread), so users
 * never wrap their {@code DataSource} by hand.
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
