package summer.issuetracker.tag;

import summer.data.jdbc.annotation.RowModel;

@RowModel(table = "tags")
public record Tag(
        Long id,
        Long orgId,
        String name,
        String color
) {}
