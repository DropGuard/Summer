package com.github.dropguard.summer.web;

import com.github.dropguard.summer.core.config.PageableProperties;

/**
 * Default resolver for {@code @Pageable} handler parameters.
 *
 * <p>Reads {@code page} and {@code size} from the query string, defaulting to {@code 0} and {@code
 * 20} (overridable via {@link PageableProperties}). Negative values are clamped to {@code 0},
 * matching the documented pagination contract.
 *
 * <p>This resolver is web-layer infrastructure and is safe to use under both engines — it holds no
 * reflection state.
 */
public class DefaultPageResolver implements HttpParameterResolver {

    private final int defaultPage;
    private final int defaultSize;

    public DefaultPageResolver(PageableProperties props) {
        // PageableProperties fields are nullable Integer; when no
        // pageable
        // config is present (and @WithDefault is not applied during binding) they
        // can be null. Fall back to the documented defaults to avoid NPE on unboxing.
        this.defaultPage = props.defaultPage() == null ? 0 : props.defaultPage();
        this.defaultSize = props.defaultSize() == null ? 20 : props.defaultSize();
    }

    @Override
    public boolean supports(HandlerParam param) {
        // This resolver owns only the built-in DefaultPageRequest. Other
        // ScrollRequest subtypes (cursor-based, limit/offset, ...) register their
        // own resolver; matching on the broad ScrollRequest marker would let this
        // resolver wrongly claim them. The framework still recognises any
        // ScrollRequest as a pageable parameter upstream — it just routes each
        // subtype to its dedicated resolver.
        return DefaultPageRequest.class.equals(param.type());
    }

    @Override
    public Object resolve(HttpContext ctx, HandlerParam param) {
        int page = parse(ctx.request().queryParam("page"), defaultPage);
        int size = parse(ctx.request().queryParam("size"), defaultSize);
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
