package summer.web;

/**
 * Static, request-scoped accessor for the active {@link RequestContext}.
 *
 * <p>
 * This is the missing piece that lets framework-agnostic layers (services, AOP
 * interceptors) read "the current request" without it being passed as a method
 * parameter. It is safe under Summer's execution model because:
 * </p>
 * <ul>
 * <li>each request runs on its own <em>virtual thread</em> (no pooling /
 * reuse), so there is no cross-request bleed;</li>
 * <li>the server entry point ({@code NettyHttpServerHandler}) always clears the
 * holder in a {@code finally} block at the end of request processing.</li>
 * </ul>
 *
 * <p>
 * Producers: the auth middleware calls {@link #set(RequestContext)} once a
 * request is authenticated. Consumers (services, {@code MethodInterceptor}s)
 * call {@link #current()}. Never call {@link #set} outside the request
 * lifecycle.
 * </p>
 */
public final class RequestContextHolder {

	private RequestContextHolder() {
	}

	private static final ThreadLocal<RequestContext> CONTEXT = new ThreadLocal<>();

	/** Installs the context for the current request. Called by auth middleware. */
	public static void set(RequestContext context) {
		CONTEXT.set(context);
	}

	/**
	 * Convenience overload for non-HTTP contexts (e.g. tests, scheduled tasks):
	 * installs a context that carries only the user id.
	 */
	public static void set(Long userId) {
		CONTEXT.set(new RequestContext(new Request(summer.web.HttpMethod.GET, "/", "", null, new byte[0]), userId));
	}

	/** The active request context, or {@code null} if none is bound. */
	public static RequestContext current() {
		return CONTEXT.get();
	}

	/** The authenticated user id of the active request, or {@code null}. */
	public static Long currentUserId() {
		RequestContext ctx = CONTEXT.get();
		return ctx == null ? null : ctx.userId();
	}

	/** Removes the binding. Called by the server entry point in a finally block. */
	public static void clear() {
		CONTEXT.remove();
	}
}
