package com.github.dropguard.summer.issuetracker.tag;

import com.github.dropguard.summer.data.jdbc.annotation.RowModel;

@RowModel(table = "tags")
public record Tag(Long id, Long orgId, String name, String color) {}
