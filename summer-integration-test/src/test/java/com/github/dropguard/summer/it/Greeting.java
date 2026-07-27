package com.github.dropguard.summer.it;

import com.github.dropguard.summer.data.jdbc.annotation.RowModel;

/** Minimal JDBC entity used to exercise a real Postgres from a framework IT. */
@RowModel(table = "greetings")
public record Greeting(Long id, String text) {}
