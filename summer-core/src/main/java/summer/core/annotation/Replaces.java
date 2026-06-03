package summer.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a replacement for another {@link Component} or
 * {@link Configuration} class. When present, the target class is excluded
 * from the application context, and this class takes over.
 *
 * <p>
 * Multiple {@code @Replaces} targeting the same configuration class is a
 * compile-time (AOT) or startup (runtime) error.
 *
 * <pre>
 * // Main application
 * &#64;Configuration
 * public class DataSourceConfig {
 *     &#64;Bean
 *     public DataSource dataSource() { return new HikariDataSource(...); }
 * }
 *
 * // Test replacement
 * &#64;Configuration
 * &#64;Replaces(DataSourceConfig.class)
 * public class TestDataSourceConfig {
 *     &#64;Bean
 *     public DataSource dataSource() { return new MockDataSource(); }
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Replaces {
	Class<?> value();
}
