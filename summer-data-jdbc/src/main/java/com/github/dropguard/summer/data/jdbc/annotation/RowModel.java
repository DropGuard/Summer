package com.github.dropguard.summer.data.jdbc.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that this Record or Class should have a RowMapper generated at
 * compile-time, and that it maps to a JDBC table.
 *
 * <p>
 * {@code table} is the physical table name the entity is persisted in. It is
 * required (no implicit naming) so the mapping is always explicit —
 * QueryBuilder and the reflective RowMapper both read it, and an empty value
 * fails fast at registration time rather than producing a blank table name in
 * generated SQL.
 * </p>
 *
 * <p>
 * Fields must use JDBC-native types: primitives ({@code int}, {@code long},
 * {@code double}, {@code boolean}) and their boxed forms, {@link String},
 * {@link java.math.BigDecimal}, {@link java.util.UUID}, and the
 * {@code java.time} types ({@code LocalDateTime}, {@code LocalDate},
 * {@code LocalTime}, {@code OffsetDateTime}). A field of any other type (e.g.
 * {@code jsonb}, nested records, {@code List}/{@code Map}) is rejected at
 * assembly with a clear error — complex structured columns require explicit
 * extension and are not mapped automatically.
 * </p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RowModel {
	String table() default "";
}
