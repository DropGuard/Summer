package com.github.dropguard.summer.it;

import com.github.dropguard.summer.data.jdbc.annotation.RowModel;
import java.time.LocalDateTime;

/**
 * Issue entity for the real-Postgres QueryBuilder contract test. Lives in the
 * framework integration-test module (not a demo) so the contract is asserted by
 * the framework itself.
 */
@RowModel(table = "it_issues")
public record ItIssue(Long id, String title, String status, String assignee, LocalDateTime createdAt) {
}
