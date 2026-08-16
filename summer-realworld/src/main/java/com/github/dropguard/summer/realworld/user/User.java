package com.github.dropguard.summer.realworld.user;

import com.github.dropguard.summer.data.jdbc.annotation.RowModel;
import java.time.LocalDateTime;

/** A RealWorld user. {@code bio}/{@code image} are optional (may be null). */
@RowModel(table = "users")
public record User(
        Long id,
        String username,
        String email,
        String password,
        String bio,
        String image,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
