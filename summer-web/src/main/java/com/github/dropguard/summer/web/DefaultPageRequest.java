package com.github.dropguard.summer.web;

/**
 * Default {@link ScrollRequest} carrying zero-based {@code page} and {@code size}.
 *
 * <p>Produced by the built-in {@code @Pageable} resolution. Moved to the web layer so the pageable
 * type and its resolution logic live together, independent of either DI engine. The parse rules
 * (defaults 0/20, negatives clamped to 0) are centralized in {@link #from(Request)} so the runtime
 * resolver and the AOT inline code emit identical results.
 */
public record DefaultPageRequest(int page, int size) implements ScrollRequest {

    /**
     * Builds a {@code DefaultPageRequest} from the current request's {@code page}/{@code size}
     * query parameters.
     *
     * @param request the HTTP request
     * @return the resolved page request
     */
    public static DefaultPageRequest from(Request request) {
        int page = parse(request.queryParam("page"), 0);
        int size = parse(request.queryParam("size"), 20);
        return new DefaultPageRequest(page, size);
    }

    private static int parse(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
