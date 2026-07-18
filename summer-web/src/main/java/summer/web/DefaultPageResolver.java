package summer.web;

import summer.core.config.PageableProperties;

/**
 * Default resolver for {@code @Pageable} handler parameters.
 *
 * <p>
 * Reads {@code page} and {@code size} from the query string, defaulting to
 * {@code 0} and {@code 20} (overridable via {@link PageableProperties}).
 * Negative values are clamped to {@code 0}, matching the documented pagination
 * contract.
 * </p>
 *
 * <p>
 * This resolver is web-layer infrastructure and is safe to use under both
 * engines — it holds no reflection state.
 * </p>
 */
public class DefaultPageResolver implements HttpParameterResolver {

	private final int defaultPage;
	private final int defaultSize;

	public DefaultPageResolver(PageableProperties props) {
		// PageableProperties fields are nullable Integer; when no summer.pageable
		// config is present (and @DefaultValue is not applied during binding) they
		// can be null. Fall back to the documented defaults to avoid NPE on unboxing.
		this.defaultPage = props.defaultPage() == null ? 0 : props.defaultPage();
		this.defaultSize = props.defaultSize() == null ? 20 : props.defaultSize();
	}

	@Override
	public boolean supports(HandlerParam param) {
		return ScrollRequest.class.isAssignableFrom(param.type());
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
