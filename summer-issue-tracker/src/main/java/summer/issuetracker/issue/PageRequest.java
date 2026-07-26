package summer.issuetracker.issue;

/** Zero-based page request for list/search endpoints. */
public record PageRequest(int page, int size) {
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public PageRequest {
        if (page < 0) {
            page = 0;
        }
        if (size <= 0) {
            size = DEFAULT_SIZE;
        }
        if (size > MAX_SIZE) {
            size = MAX_SIZE;
        }
    }

    public int offset() {
        return page * size;
    }
}
