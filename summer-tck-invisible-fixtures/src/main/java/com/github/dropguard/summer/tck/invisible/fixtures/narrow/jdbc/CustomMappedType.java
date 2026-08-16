package com.github.dropguard.summer.tck.invisible.fixtures.narrow.jdbc;

/**
 * Plain row type for the producer-init regression: NOT a {@code @RowModel}, so the only {@code
 * RowMapper} for it can come from the {@code @Bean} producer's own {@code registerMapper} call —
 * never from the AOT bake (which only covers {@code @RowModel}s) nor the reflective registrar.
 */
public record CustomMappedType(int id, String name) {}
