package com.github.dropguard.summer.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class or method as a replacement for another bean.
 *
 * <p><strong>Class-level usage:</strong> Replaces the entire configuration class. The replacement
 * class must provide all {@code @Bean} methods of the target.
 *
 * <p><strong>Method-level usage:</strong> Replaces a {@code @Bean} method by return type. If
 * multiple {@code @Bean} methods return the same type, an error is thrown.
 *
 * <pre>
 * // Class-level: replaces entire config
 * &#64;Configuration
 * &#64;Replaces(DataSourceConfig.class)
 * public class TestDataSourceConfig {
 * 	&#64;Bean
 * 	public DataSource dataSource() {
 * 		return new MockDataSource();
 * 	}
 * }
 *
 * // Method-level: replaces by return type
 * &#64;Configuration
 * public class TestConfig {
 * 	&#64;Bean
 * 	&#64;Replaces(DataSource.class)
 * 	public DataSource dataSource() {
 * 		return new MockDataSource();
 * 	}
 * }
 * </pre>
 *
 * <p><strong>Contract.</strong> Matching runs against the whole candidate universe — application,
 * framework, and test archives alike; a test-slice configuration may replace a production one. A
 * missing target fails startup with {@code NoSuchBeanException}: replacement is an assertion that
 * the target exists, not an optional override. The survivor is always the replacing definition;
 * every bean whose class (class-level) or producer return type (method-level) matches the target is
 * removed before condition evaluation.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Replaces {
    Class<?> value();
}
