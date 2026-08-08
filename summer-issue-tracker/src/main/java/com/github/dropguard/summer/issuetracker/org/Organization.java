package com.github.dropguard.summer.issuetracker.org;

import com.github.dropguard.summer.data.jdbc.annotation.RowModel;
import java.time.OffsetDateTime;

@RowModel(table = "organizations")
public record Organization(Long id, String name, String slug, OffsetDateTime createdAt) {}
