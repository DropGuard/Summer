package summer.fixtures.data.jdbc;

import summer.data.jdbc.annotation.RowModel;

@RowModel(table = "users")
public record User(int id, String name) {
}
