package com.github.dropguard.summer.web;

import com.github.dropguard.summer.core.config.PageableProperties;

/**
 * Built-in resolver for {@link CursorPageable} handler parameters — the framework's out-of-the-box
 * cursor paging, symmetric to {@link DefaultPageResolver} (offset paging).
 *
 * <p>Reads {@code cursor} and {@code limit} from the query string. A missing or unparsable cursor
 * means "start from the beginning" (null), unlike offset paging where the default is page 0. A
 * missing {@code limit} falls back to {@code 20}; negative limits are clamped to {@code 0},
 * matching the documented pagination contract.
 *
 * <p>This resolver owns only {@link CursorPageable}; other {@link ScrollRequest} subtypes register
 * their own resolver. It holds no reflection state and works identically on both DI engines (the
 * AOT engine routes pageable parameters through the same {@link HttpParameterResolverChain}).
 */
public class CursorPageResolver implements HttpParameterResolver {

    private final int defaultLimit;

    public CursorPageResolver(PageableProperties props) {
        this.defaultLimit = props.defaultSize() == null ? 20 : props.defaultSize();
    }

    @Override
    public boolean supports(HandlerParam param) {
        return CursorPageable.class.equals(param.type());
    }

    @Override
    public Object resolve(HttpContext ctx, HandlerParam param) {
        Long cursor = parseCursor(ctx.request().queryParam("cursor"));
        int limit = parseLimit(ctx.request().queryParam("limit"), defaultLimit);
        return new CursorPageable(cursor, limit);
    }

    private static Long parseCursor(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value);
            return parsed < 0 ? null : parsed;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int parseLimit(String value, int defaultValue) {
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
