package com.github.dropguard.summer.issuetracker.audit;

import com.github.dropguard.summer.data.jdbc.annotation.RowModel;
import java.time.OffsetDateTime;

/**
 * A system-level audit event (Jira-style), independent of the business entity it describes. Unlike
 * {@link IssueHistory} — which is a child resource of an issue and is cascade-deleted with it — a
 * system audit event is self-contained: the target's identity is frozen into {@code
 * targetType}/{@code targetId}/{@code targetKey} at write time, and the row has no foreign key to
 * the live entity. Deleting the issue, project, or member leaves the audit trail intact.
 */
@RowModel(table = "audit_events")
public record SystemAudit(
        Long id,
        Long orgId,
        Long actorId,
        String action,
        String targetType,
        Long targetId,
        String targetKey,
        OffsetDateTime occurredAt) {}
