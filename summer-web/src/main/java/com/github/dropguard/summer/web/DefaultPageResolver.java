mport com.github.dropguard.summer.core.Internal;
package com.github.dropguard.summer.web;

mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.config.PageableProperties;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
/**
mport com.github.dropguard.summer.core.Internal;
 * Default resolver for {@code @Pageable} handler parameters.
mport com.github.dropguard.summer.core.Internal;
 *
@Internal
mport com.github.dropguard.summer.core.Internal;
 * <p>Reads {@code page} and {@code size} from the query string, defaulting to {@code 0} and {@code
mport com.github.dropguard.summer.core.Internal;
 * 20} (overridable via {@link PageableProperties}). Negative values are clamped to {@code 0},
mport com.github.dropguard.summer.core.Internal;
 * matching the documented pagination contract.
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <p>This resolver is web-layer infrastructure and is safe to use under both engines — it holds no
mport com.github.dropguard.summer.core.Internal;
 * reflection state.
mport com.github.dropguard.summer.core.Internal;
 */
mport com.github.dropguard.summer.core.Internal;
public class DefaultPageResolver implements HttpParameterResolver {
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private final int defaultPage;
mport com.github.dropguard.summer.core.Internal;
    private final int defaultSize;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    public DefaultPageResolver(PageableProperties props) {
mport com.github.dropguard.summer.core.Internal;
        // PageableProperties fields are nullable Integer; when no
mport com.github.dropguard.summer.core.Internal;
        // pageable
mport com.github.dropguard.summer.core.Internal;
        // config is present (and @WithDefault is not applied during binding) they
mport com.github.dropguard.summer.core.Internal;
        // can be null. Fall back to the documented defaults to avoid NPE on unboxing.
mport com.github.dropguard.summer.core.Internal;
        this.defaultPage = props.defaultPage() == null ? 0 : props.defaultPage();
mport com.github.dropguard.summer.core.Internal;
        this.defaultSize = props.defaultSize() == null ? 20 : props.defaultSize();
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public boolean supports(HandlerParam param) {
mport com.github.dropguard.summer.core.Internal;
        // This resolver owns only the built-in DefaultPageRequest. Other
mport com.github.dropguard.summer.core.Internal;
        // ScrollRequest subtypes (cursor-based, limit/offset, ...) register their
mport com.github.dropguard.summer.core.Internal;
        // own resolver; matching on the broad ScrollRequest marker would let this
mport com.github.dropguard.summer.core.Internal;
        // resolver wrongly claim them. The framework still recognises any
mport com.github.dropguard.summer.core.Internal;
        // ScrollRequest as a pageable parameter upstream — it just routes each
mport com.github.dropguard.summer.core.Internal;
        // subtype to its dedicated resolver.
mport com.github.dropguard.summer.core.Internal;
        return DefaultPageRequest.class.equals(param.type());
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public Object resolve(HttpContext ctx, HandlerParam param) {
mport com.github.dropguard.summer.core.Internal;
        int page = parse(ctx.request().queryParam("page"), defaultPage);
mport com.github.dropguard.summer.core.Internal;
        int size = parse(ctx.request().queryParam("size"), defaultSize);
mport com.github.dropguard.summer.core.Internal;
        return new DefaultPageRequest(page, size);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private static int parse(String value, int defaultValue) {
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
