package summer.twitter.user;

import summer.data.jdbc.annotation.RowModel;
import java.time.ZonedDateTime;

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
    ZonedDateTime createdAt
) {}