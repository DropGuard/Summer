package summer.fixtures.data.jdbc;

import summer.data.jdbc.annotation.RowModel;

@RowModel
public record User(int id, String name) {
}
