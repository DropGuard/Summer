package com.github.dropguard.summer.tck.invisible.fixtures.narrow.jdbc;

import com.github.dropguard.summer.data.jdbc.annotation.RowModel;

/** Seeded {@code @RowModel} for the subclass regression — must be baked by the AOT engine. */
@RowModel(table = "subclass_users")
public record SubclassUser(Integer id, String name) {}
