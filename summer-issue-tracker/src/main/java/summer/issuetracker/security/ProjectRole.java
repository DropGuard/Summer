package summer.issuetracker.security;

/** Project-level membership roles (resolved from project_members). */
public enum ProjectRole {
    VIEWER,
    MEMBER,
    MANAGER;

    public boolean hasAtLeast(ProjectRole other) {
        return this.ordinal() >= other.ordinal();
    }
}
