mport com.github.dropguard.summer.core.Internal;
package com.github.dropguard.summer.web;

mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.config.PageableProperties;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
/**
mport com.github.dropguard.summer.core.Internal;
 * Built-in resolver for {@link CursorPageable} handler parameters — the framework's out-of-the-box
mport com.github.dropguard.summer.core.Internal;
 * cursor paging, symmetric to {@link DefaultPageResolver} (offset paging).
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
@Internal
 * <p>Reads {@code cursor} and {@code limit} from the query string. A missing or unparsable cursor
mport com.github.dropguard.summer.core.Internal;
 * means "start from the beginning" (null), unlike offset paging where the default is page 0. A
mport com.github.dropguard.summer.core.Internal;
 * missing {@code limit} falls back to {@code 20}; negative limits are clamped to {@code 0},
mport com.github.dropguard.summer.core.Internal;
 * matching the documented pagination contract.
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <p>This resolver owns only {@link CursorPageable}; other {@link ScrollRequest} subtypes register
mport com.github.dropguard.summer.core.Internal;
 * their own resolver. It holds no reflection state and works identically on both DI engines (the
mport com.github.dropguard.summer.core.Internal;
 * AOT engine routes pageable parameters through the same {@link HttpParameterResolverChain}).
mport com.github.dropguard.summer.core.Internal;
 */
mport com.github.dropguard.summer.core.Internal;
public class CursorPageResolver implements HttpParameterResolver {
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private final int defaultLimit;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    public CursorPageResolver(PageableProperties props) {
mport com.github.dropguard.summer.core.Internal;
        this.defaultLimit = props.defaultSize() == null ? 20 : props.defaultSize();
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public boolean supports(HandlerParam param) {
mport com.github.dropguard.summer.core.Internal;
        return CursorPageable.class.equals(param.type());
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public Object resolve(HttpContext ctx, HandlerParam param) {
mport com.github.dropguard.summer.core.Internal;
        Long cursor = parseCursor(ctx.request().queryParam("cursor"));
mport com.github.dropguard.summer.core.Internal;
        int limit = parseLimit(ctx.request().queryParam("limit"), defaultLimit);
mport com.github.dropguard.summer.core.Internal;
        return new CursorPageable(cursor, limit);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private static Long parseCursor(String value) {
mport com.github.dropguard.summer.core.Internal;
        if (value == null || value.isBlank()) {
mport com.github.dropguard.summer.core.Internal;
            return null;
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        try {
mport com.github.dropguard.summer.core.Internal;
            long parsed = Long.parseLong(value);
mport com.github.dropguard.summer.core.Internal;
            return parsed < 0 ? null : parsed;
mport com.github.dropguard.summer.core.Internal;
        } catch (NumberFormatException e) {
mport com.github.dropguard.summer.core.Internal;
            return null;
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private static int parseLimit(String value, int defaultValue) {
mport com.github.dropguard.summer.core.Internal;
        if (value == null || value.isBlank()) {
mport com.github.dropguard.summer.core.Internal;
            return defaultValue;
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        try {
mport com.github.dropguard.summer.core.Internal;
            return Math.max(0, Integer.parseInt(value));
mport com.github.dropguard.summer.core.Internal;
        } catch (NumberFormatException e) {
mport com.github.dropguard.summer.core.Internal;
            return defaultValue;
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;
}
