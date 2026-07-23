package summer.it;

import summer.data.jdbc.annotation.RowModel;

/** Minimal JDBC entity used to exercise a real Postgres from a framework IT. */
@RowModel(table = "greetings")
public record Greeting(Long id, String text) {
}
