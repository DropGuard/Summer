package summer.issuetracker.security;

/** Organization-level roles. Ordered by privilege. */
public enum Role {
    GUEST,
    MEMBER,
    MANAGER,
    ADMIN;

    public boolean hasAtLeast(Role other) {
        return this.ordinal() >= other.ordinal();
    }
}
