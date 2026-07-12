package summer.twitter.user;

import summer.data.jdbc.annotation.RowModel;
import java.time.OffsetDateTime;

@RowModel
public record User(
    Long id,
    String username,
    String displayName,
    String email,
    String passwordHash,
    String bio,
    Integer followerCount,
    Integer followingCount,
    OffsetDateTime createdAt
) {}