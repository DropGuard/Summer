package com.github.dropguard.summer.web.http;

import com.github.dropguard.summer.web.Handler;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpMethod;
import com.github.dropguard.summer.web.HttpRouter;
import com.github.dropguard.summer.web.RadixTrie;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * High-performance router implementation using a Radix Tree (Trie).
 *
 * <p>
 * This implementation uses one {@link RadixTrie} per HTTP method for efficient
 * path matching. Path segment matching is performed at the byte level to
 * minimize String allocations during routing.
 * </p>
 *
 * <p>
 * This class has a public {@link #register(HttpMethod, String, Handler)} method
 * for route registration, but this method is <strong>not</strong> part of the
 * {@link HttpRouter} interface. Only the {@link HttpRouter.Builder} (framework
 * internals) can call {@code register()}. At runtime, consumers hold an
 * {@link HttpRouter} reference and can only call {@link #route(HttpContext)}.
 * </p>
 */
public class RadixTreeHttpRouter implements HttpRouter {

	private final Map<String, RadixTrie<Handler>> tries = new HashMap<>();

	/**
	 * Creates an empty RadixTreeHttpRouter.
	 */
	public RadixTreeHttpRouter() {
	}

	/**
	 * Creates a RadixTreeHttpRouter and registers the given routes.
	 *
	 * @param routes
	 *            the routes to register
	 */
	public RadixTreeHttpRouter(List<HttpRouter.Builder.Route> routes) {
		for (HttpRouter.Builder.Route route : routes) {
			register(route.method(), route.path(), route.handler());
		}
	}

	/**
	 * Registers a handler for the given HTTP method and path pattern.
	 *
	 * @param method
	 *            the HTTP method (GET, POST, etc.)
	 * @param path
	 *            the path pattern (e.g., "/users/{id}")
	 * @param handler
	 *            the request handler
	 */
	public void register(HttpMethod method, String path, Handler handler) {
		tries.computeIfAbsent(method.name(), k -> new RadixTrie<>()).insert(path, handler);
	}

	/**
	 * Matches a request against the trie and dispatches to the appropriate handler.
	 */
	@Override
	public void route(HttpContext ctx) {
		HttpMethod method = ctx.request().getMethod();
		RadixTrie<Handler> trie = tries.get(method.name());
		if (trie == null) {
			return;
		}

		byte[] path = ctx.request().getRawPathBytes();
		RadixTrie.MatchResult<Handler> result = trie.match(path);
		if (result == null) {
			return;
		}

		result.params().forEach(ctx.request()::setPathParam);
		result.handler().handle(ctx);
	}
}
