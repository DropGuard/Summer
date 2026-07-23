package summer.it;

import java.time.LocalDateTime;
import summer.data.jdbc.annotation.RowModel;

/**
 * Issue entity for the real-Postgres QueryBuilder contract test. Lives in the
 * framework integration-test module (not a demo) so the contract is asserted by
 * the framework itself.
 */
@RowModel(table = "it_issues")
public record ItIssue(Long id, String title, String status, String assignee, LocalDateTime createdAt) {
}
