package com.github.dropguard.summer.core.data;

/**
 * Offset/limit pagination request for APIs that page by a start position and a row count (the
 * RealWorld API convention), as opposed to the zero-based {@code page}/{@code size} of {@link
 * PageRequest}.
 *
 * <p>Invalid inputs are clamped to sane defaults so a caller can never trigger an unbounded or
 * negative query: a negative offset becomes {@code 0}, and an out-of-range limit is clamped to
 * {@link #DEFAULT_LIMIT} / {@link #MAX_LIMIT}. The maximum limit protects the database from a
 * client asking for the whole table in one page.
 */
public record LimitOffsetPageRequest(int offset, int limit) {
    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;

    public LimitOffsetPageRequest {
        if (offset < 0) {
            offset = 0;
        }
        if (limit <= 0) {
            limit = DEFAULT_LIMIT;
        }
        if (limit > MAX_LIMIT) {
            limit = MAX_LIMIT;
        }
    }

    /**
     * Converts to a zero-based {@link PageRequest} for the same window when the page size equals
     * this limit. This is only meaningful when the offset is an exact multiple of the limit;
     * otherwise prefer the offset/limit form directly.
     */
    public PageRequest toPageRequest() {
        return new PageRequest(offset / limit, limit);
    }
}
