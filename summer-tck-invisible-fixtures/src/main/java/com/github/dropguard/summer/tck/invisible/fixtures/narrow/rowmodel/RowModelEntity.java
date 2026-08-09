package com.github.dropguard.summer.tck.invisible.fixtures.narrow.rowmodel;

import com.github.dropguard.summer.data.jdbc.annotation.RowModel;

/** Seeded @RowModel for the narrow dual-engine metadata regression. */
@RowModel(table = "row_model_entity")
public record RowModelEntity(Long id, String name) {}
