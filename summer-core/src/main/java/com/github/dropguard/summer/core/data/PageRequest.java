package com.github.dropguard.summer.core.data;

/**
 * Zero-based page request for list/search endpoints.
 *
 * <p>Carries a page index and page size; invalid inputs are clamped to sane defaults so a caller
 * can never trigger an unbounded or negative-page query. The default and maximum sizes protect the
 * database from a client asking for the whole table in one page.
 */
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

    /** The zero-based offset for the SQL {@code OFFSET} clause, {@code page * size}. */
    public int offset() {
        return page * size;
    }
}
