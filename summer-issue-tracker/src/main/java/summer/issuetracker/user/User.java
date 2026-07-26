package summer.issuetracker.user;

import summer.data.jdbc.annotation.RowModel;

import java.time.OffsetDateTime;

/**
 * Stored user. {@code passwordHash} is the only credential field; the demo
 * never returns it through the API layer (see UserView).
 */
@RowModel(table = "users")
public record User(
        Long id,
        Long orgId,
        String username,
        String displayName,
        String email,
        String passwordHash,
        String role,
        OffsetDateTime createdAt
) {}
