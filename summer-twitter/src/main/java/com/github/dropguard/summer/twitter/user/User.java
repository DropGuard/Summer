package com.github.dropguard.summer.twitter.user;

import com.github.dropguard.summer.data.jdbc.annotation.RowModel;
import java.time.OffsetDateTime;

@RowModel(table = "users")
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