package summer.runtime;

import java.lang.reflect.Parameter;
import summer.core.config.PageableProperties;
import summer.web.HttpContext;
import summer.web.PageRequest;
import summer.web.Pageable;
import summer.web.Sort;

/**
 * Resolves {@link Pageable} parameters from HTTP request query parameters.
 *
 * <p>
 * Supports the following query parameters:
 * </p>
 * <ul>
 * <li>{@code page} - zero-based page index (default: configurable via
 * {@link summer.core.config.PageableProperties})</li>
 * <li>{@code size} - number of items per page (default: configurable via
 * {@link summer.core.config.PageableProperties})</li>
 * <li>{@code sort} - sort specification in format "property,direction" (e.g.,
 * "createdAt,desc")</li>
 * </ul>
 *
 * <p>
 * Usage in controllers:
 * </p>
 *
 * <pre>
 * &#64;Get("/articles")
 * public void listArticles(HttpContext ctx, Pageable pageable) {
 * 	// pageable is automatically resolved from query parameters
 * 	int offset = pageable.getPageNumber() * pageable.getPageSize();
 * 	List&lt;Article&gt; articles = service.findAll(offset, pageable.getPageSize());
 * }
 * </pre>
 *
 * @see Pageable
 * @see PageRequest
 * @see summer.core.config.PageableProperties
 */
public class PageableResolver implements HttpParameterResolver {

	private final int defaultPage;
	private final int defaultSize;

	/**
	 * Creates a new PageableResolver with defaults from {@link PageableProperties}.
	 *
	 * @param props
	 *            pagination configuration properties
	 */
	public PageableResolver(PageableProperties props) {
		this.defaultPage = props.defaultPage();
		this.defaultSize = props.defaultSize();
	}

	@Override
	public boolean supports(Parameter parameter) {
		return Pageable.class.isAssignableFrom(parameter.getType());
	}

	@Override
	public Object resolve(HttpContext ctx, Parameter parameter) {
		int page = parseQueryParam(ctx, "page", defaultPage);
		int size = parseQueryParam(ctx, "size", defaultSize);
		Sort sort = parseSort(ctx);

		return PageRequest.of(page, size, sort);
	}

	private int parseQueryParam(HttpContext ctx, String name, int defaultValue) {
		String value = ctx.queryParam(name);
		if (value == null || value.isBlank()) {
			return defaultValue;
		}
		try {
			int parsed = Integer.parseInt(value);
			return Math.max(0, parsed);
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	private Sort parseSort(HttpContext ctx) {
		String sortParam = ctx.queryParam("sort");
		if (sortParam == null || sortParam.isBlank()) {
			return Sort.unsorted();
		}
		return Sort.parse(sortParam);
	}
}
