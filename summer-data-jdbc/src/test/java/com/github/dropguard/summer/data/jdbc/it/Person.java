package com.github.dropguard.summer.data.jdbc.it;

import com.github.dropguard.summer.data.jdbc.annotation.RowModel;

/**
 * Minimal shared entity for the framework's real-database integration contracts (JDBC round-trip,
 * QueryBuilder selection/update/count). Deliberately generic and free of any business-domain
 * meaning so it cannot be mistaken for a demo's domain model (e.g. an issue tracker's Issue).
 */
@RowModel(table = "persons")
public record Person(Long id, String name, int age, String status) {}
