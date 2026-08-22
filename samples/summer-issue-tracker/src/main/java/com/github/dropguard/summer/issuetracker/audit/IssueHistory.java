package com.github.dropguard.summer.issuetracker.audit;

import com.github.dropguard.summer.data.jdbc.annotation.RowModel;
import java.time.OffsetDateTime;

/**
 * An issue's change history: one append-only row per meaningful mutation (status, assignee,
 * priority, title). It is a <em>child resource of the issue</em> — not a system-level audit log —
 * so it is scoped to the issue's lifetime and removed with it (see the FK {@code ON DELETE CASCADE}
 * in the schema). Written inside the same {@code @Transactional} boundary as the mutation it
 * records.
 */
@RowModel(table = "issue_history")
public record IssueHistory(
        Long id,
        Long issueId,
        Long actorId,
        String action,
        String fromValue,
        String toValue,
        OffsetDateTime createdAt) {}
