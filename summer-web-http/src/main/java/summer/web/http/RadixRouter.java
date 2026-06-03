package summer.web.http;

import java.util.HashMap;
import java.util.Map;
import summer.core.Component;
import summer.web.Handler;
import summer.web.HttpRouter;
import summer.web.HttpContext;
import summer.web.PathUtils;

/**
 * High-performance router implementation using a Radix Tree (Trie). Path
 * segment matching is performed at the byte level to minimize String
 * allocations during routing. Path parameter extraction still requires String
 * creation.
 */
@Component
public class RadixRouter implements HttpRouter {

	private final Node root = new Node();

	/**
	 * Navigates the trie to the node for the given path, creating nodes as needed.
	 * Returns the terminal node for the path.
	 *
	 * <p>
	 * Supports path parameters ({@code {name}}), single-segment wildcards
	 * ({@code *}), and multi-segment wildcards ({@code **}).
	 * </p>
	 */
	private Node navigateToNode(String path) {
		String[] segments = tokenize(path);
		Node current = root;

		for (String segment : segments) {
			current = navigateSegment(current, segment, path);
		}
		return current;
	}

	private Node navigateSegment(Node current, String segment, String path) {
		if ("**".equals(segment)) {
			if (current.catchAllChild == null) {
				current.catchAllChild = new Node();
			}
			return current.catchAllChild;
		} else if ("*".equals(segment)) {
			if (current.wildcardChild == null) {
				current.wildcardChild = new Node();
			}
			return current.wildcardChild;
		} else if (segment.startsWith("{") && segment.endsWith("}")) {
			String paramName = segment.substring(1, segment.length() - 1);
			if (current.paramChild != null && !current.paramName.equals(paramName)) {
				throw new summer.web.exception.RouteConflictException(path);
			}
			if (current.paramChild == null) {
				current.paramChild = new Node();
				current.paramName = paramName;
			}
			return current.paramChild;
		} else {
			return current.staticChildren.computeIfAbsent(segment, k -> new Node(k));
		}
	}

	@Override
	public void register(String method, String path, Handler handler) {
		navigateToNode(PathUtils.normalizePath(path)).handlers.put(method.toUpperCase(), handler);
	}

	@Override
	public void get(String path, Handler handler) {
		register("GET", path, handler);
	}

	@Override
	public void post(String path, Handler handler) {
		register("POST", path, handler);
	}

	@Override
	public void put(String path, Handler handler) {
		register("PUT", path, handler);
	}

	@Override
	public void delete(String path, Handler handler) {
		register("DELETE", path, handler);
	}

	/**
	 * Matches a request against the trie using zero-allocation byte-level scanning.
	 */
	@Override
	public Object route(HttpContext ctx) {
		byte[] path = ctx.request().getRawPathBytes();
		if (isRootPath(path)) {
			return dispatch(root, ctx);
		}

		Map<String, String> params = new HashMap<>();
		Node current = matchPath(path, params);
		if (current == null) {
			return null;
		}

		params.forEach(ctx.request()::setAttribute);
		return dispatch(current, ctx);
	}

	private boolean isRootPath(byte[] path) {
		return path == null || path.length == 0 || (path.length == 1 && path[0] == '/');
	}

	private Node matchPath(byte[] path, Map<String, String> params) {
		Node current = root;
		int start = 0;
		for (int i = 0; i <= path.length; i++) {
			if (i == path.length || path[i] == '/') {
				if (i > start) {
					if (current.catchAllChild != null) {
						current = current.catchAllChild;
						break;
					}
					current = findNext(current, path, start, i, params);
					if (current == null) {
						return null;
					}
				}
				start = i + 1;
			}
		}

		// If we ended on a node with catchAllChild, use it
		if (current.catchAllChild != null && current.handlers.isEmpty()) {
			current = current.catchAllChild;
		}
		return current;
	}

	private Object dispatch(Node node, HttpContext ctx) {
		Handler handler = node.handlers.get(ctx.request().getMethod().toUpperCase());
		return handler != null ? handler.handle(ctx) : null;
	}

	/**
	 * Finds the next node in the trie for the given path segment.
	 *
	 * <p>
	 * Matching priority:
	 * </p>
	 * <ol>
	 * <li>Static children (exact match)</li>
	 * <li>Path parameter ({@code {name}})</li>
	 * <li>Single-segment wildcard ({@code *})</li>
	 * <li>Multi-segment wildcard ({@code **}) - handled by caller</li>
	 * </ol>
	 */
	private Node findNext(Node current, byte[] path, int start, int end, Map<String, String> params) {
		// Try static children first — linear scan is faster than HashMap for small N
		for (Node child : current.staticChildren.values()) {
			if (bytesEqual(child.nameBytes, path, start, end)) {
				return child;
			}
		}
		// Fall back to parameterized child (e.g., {id})
		if (current.paramChild != null) {
			String raw = new String(path, start, end - start, java.nio.charset.StandardCharsets.UTF_8);
			String paramValue = java.net.URLDecoder.decode(raw, java.nio.charset.StandardCharsets.UTF_8);
			params.put(current.paramName, paramValue);
			return current.paramChild;
		}
		// Fall back to single-segment wildcard (*)
		if (current.wildcardChild != null) {
			return current.wildcardChild;
		}
		return null;
	}

	/**
	 * Compares a node's name bytes against a segment of the path bytes. This avoids
	 * creating String objects for comparison.
	 */
	private boolean bytesEqual(byte[] segment, byte[] path, int start, int end) {
		if (segment.length != (end - start))
			return false;
		for (int i = 0; i < segment.length; i++) {
			if (segment[i] != path[start + i])
				return false;
		}
		return true;
	}

	private String[] tokenize(String path) {
		if (path == null || path.equals("/") || path.isEmpty()) {
			return new String[0];
		}
		return java.util.Arrays.stream(path.split("/")).filter(s -> !s.isEmpty()).toArray(String[]::new);
	}

	private static class Node {
		final byte[] nameBytes;
		Map<String, Node> staticChildren = new HashMap<>();
		Node paramChild = null;
		String paramName = null;
		Node wildcardChild = null; // * matches any single segment
		Node catchAllChild = null; // ** matches rest of path
		Map<String, Handler> handlers = new HashMap<>(); // HTTP Method -> Handler

		Node() {
			this.nameBytes = new byte[0];
		}
		Node(String name) {
			this.nameBytes = name.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		}
	}
}
