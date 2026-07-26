package summer.issuetracker.issue;

/**
 * Filter criteria for {@link IssueRepository#search}. All fields nullable —
 * {@code null} means "no constraint on this dimension". This is the structured
 * input behind the UI's dynamic issue board filter.
 */
public record IssueFilter(
        Long assigneeId,
        String status,
        String priority,
        Long reporterId,
        String titleContains,
        Long tagId
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long assigneeId;
        private String status;
        private String priority;
        private Long reporterId;
        private String titleContains;
        private Long tagId;

        public Builder assigneeId(Long v) { this.assigneeId = v; return this; }
        public Builder status(String v) { this.status = v; return this; }
        public Builder priority(String v) { this.priority = v; return this; }
        public Builder reporterId(Long v) { this.reporterId = v; return this; }
        public Builder titleContains(String v) { this.titleContains = v; return this; }
        public Builder tagId(Long v) { this.tagId = v; return this; }

        public IssueFilter build() {
            return new IssueFilter(assigneeId, status, priority, reporterId, titleContains, tagId);
        }
    }
}
