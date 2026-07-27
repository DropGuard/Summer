package com.github.dropguard.summer.fixtures.data.jdbc;

import com.github.dropguard.summer.data.jdbc.annotation.RowModel;

@RowModel(table = "users")
public record User(int id, String name) {}
