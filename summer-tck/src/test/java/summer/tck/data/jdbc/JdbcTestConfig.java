package summer.tck.data.jdbc;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;
import summer.data.jdbc.JdbcTemplate;

/**
 * Test configuration that registers a {@link DataSource} and
 * {@link JdbcTemplate} as real DI beans — so both engines (Runtime + AOT) wire
 * them through the container rather than via {@code new JdbcTemplate(...)}.
 *
 * <p>
 * With {@link JdbcTemplate} present as a bean, {@code RowMapperRegistrar}
 * (conditional on {@code JdbcTemplate}) auto-registers every {@code @RowModel}
 * it discovers through the unified test universe ({@code IndexUniverse} merges
 * {@code jandex.idx} + {@code jandex-test.idx}). The test-tree
 * {@code @RowModel} {@code User} is therefore discovered and mapped on BOTH
 * engines — the real dual-engine path this TCK must verify. No manual
 * {@code registerMapper} here: doing so would bypass {@code RowMapperRegistrar}
 * and hide exactly the gap this TCK exists to catch.
 * </p>
 *
 * <p>
 * The previous TCK bypassed the container with a manually-constructed
 * {@code JdbcTemplate} whose Runtime/AOT bodies were byte-identical, so it was
 * a decoy with no real engine differentiation.
 * </p>
 */
@Configuration
public class JdbcTestConfig {

	@Bean
	public DataSource jdbcTestDataSource() {
		HikariConfig config = new HikariConfig();
		config.setJdbcUrl("jdbc:h2:mem:tck_jdbc;DB_CLOSE_DELAY=-1");
		config.setUsername("sa");
		config.setPassword("");
		return new HikariDataSource(config);
	}

	@Bean
	public JdbcTemplate jdbcTestJdbcTemplate(DataSource dataSource) {
		return new JdbcTemplate(dataSource);
	}
}
