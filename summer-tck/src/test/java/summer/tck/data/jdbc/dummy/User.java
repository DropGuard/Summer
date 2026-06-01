package summer.tck.data.jdbc.dummy;

import summer.data.jdbc.annotation.RowModel;

@RowModel
public record User(int id, String name) {
}
