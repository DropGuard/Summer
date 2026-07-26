package com.github.dropguard.summer.web;

/**
 * Typed constants for well-known request attribute keys.
 *
 * <p>
 * Eliminates magic strings and provides compile-time safety for framework
 * internal attributes. User-defined attributes should define their own
 * constants following this pattern.
 * </p>
 *
 * <pre>{@code
 * // In middleware:
 * ctx.request().setAttribute(RequestAttributes.USER_ID, userId);
 *
 * // In resolver:
 * Long userId = ctx.request().getAttribute(RequestAttributes.USER_ID);
 * }</pre>
 */
public final class RequestAttributes {

	private RequestAttributes() {
	}

	/** Attribute key for the authenticated user ID (set by auth middleware). */
	public static final AttributeKey<Long> USER_ID = new AttributeKey<>("userId");

	/** Attribute key for the last exception thrown during request processing. */
	public static final AttributeKey<Throwable> LAST_EXCEPTION = new AttributeKey<>("last_exception");

	/**
	 * A typed key for a request attribute.
	 *
	 * @param <T>
	 *            the attribute value type
	 */
	public record AttributeKey<T>(String name) {
	}
}
