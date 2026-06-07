package summer.example;

import summer.data.jdbc.annotation.RowModel;

@RowModel
public record User(String id, String name, String email) {
}
