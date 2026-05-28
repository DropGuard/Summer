package summer.web;

import java.util.HashMap;
import java.util.Map;
import summer.core.Component;

/**
 * High-performance router implementation using a Radix Tree (Trie) optimized
 * for zero-allocation byte-level routing.
 */
@Component
public class Router {

	private final Node root = new Node();

	public void register(String method, String path, Handler handler) {
		String[] segments = tokenize(path);
		Node current = root;

		for (String segment : segments) {
			if (segment.startsWith("{") && segment.endsWith("}")) {
				String paramName = segment.substring(1, segment.length() - 1);
				if (current.paramChild != null && !current.paramName.equals(paramName)) {
					throw new summer.web.exception.RouteConflictException(path);
				}
				if (current.paramChild == null) {
					current.paramChild = new Node();
					current.paramName = paramName;
				}
				current = current.paramChild;
			} else {
				current = current.staticChildren.computeIfAbsent(segment, k -> new Node(k));
			}
		}
		current.handlers.put(method.toUpperCase(), handler);
	}

	public void get(String path, Handler handler) {
		register("GET", path, handler);
	}

	public void post(String path, Handler handler) {
		register("POST", path, handler);
	}

	public void put(String path, Handler handler) {
		register("PUT", path, handler);
	}

	public void delete(String path, Handler handler) {
		register("DELETE", path, handler);
	}

	/**
	 * Matches a request against the trie using zero-allocation byte-level scanning.
	 */
	public Object route(WebContext ctx) {
		byte[] path = ctx.request().getRawPathBytes();
		if (path == null || path.length == 0 || (path.length == 1 && path[0] == '/')) {
			return dispatch(root, ctx);
		}

		Node current = root;
		int start = 0;
		for (int i = 0; i <= path.length; i++) {
			if (i == path.length || path[i] == '/') {
				if (i > start) {
					// Segment found: path[start...i-1]
					current = findNext(current, path, start, i, ctx);
					if (current == null)
						return null;
				}
				start = i + 1;
			}
		}

		return dispatch(current, ctx);
	}

	private Node findNext(Node current, byte[] path, int start, int end, WebContext ctx) {
		// 1. Try static children
		for (Node child : current.staticChildren.values()) {
			if (bytesEqual(child.nameBytes, path, start, end)) {
				return child;
			}
		}

		// 2. Try parameter child
		if (current.paramChild != null) {
			String raw = new String(path, start, end - start, java.nio.charset.StandardCharsets.UTF_8);
			String paramValue = java.net.URLDecoder.decode(raw, java.nio.charset.StandardCharsets.UTF_8);
			ctx.request().setAttribute(current.paramName, paramValue);
			return current.paramChild;
		}

		return null;
	}

	private Object dispatch(Node node, WebContext ctx) {
		Handler handler = node.handlers.get(ctx.request().getMethod().toUpperCase());
		return handler != null ? handler.handle(ctx) : null;
	}

	public void ws(String path, summer.web.websocket.WebSocketHandler handler) {
		String[] segments = tokenize(path);
		Node current = root;

		for (String segment : segments) {
			if (segment.startsWith("{") && segment.endsWith("}")) {
				String paramName = segment.substring(1, segment.length() - 1);
				if (current.paramChild != null && !current.paramName.equals(paramName)) {
					throw new summer.web.exception.RouteConflictException(path);
				}
				if (current.paramChild == null) {
					current.paramChild = new Node();
					current.paramName = paramName;
				}
				current = current.paramChild;
			} else {
				current = current.staticChildren.computeIfAbsent(segment, k -> new Node(k));
			}
		}
		current.wsHandler = handler;
	}

	public WsMatch routeWs(String pathStr) {
		byte[] path = pathStr.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		Map<String, String> pathParams = new HashMap<>();
		if (path == null || path.length == 0 || (path.length == 1 && path[0] == '/')) {
			return root.wsHandler != null ? new WsMatch(root.wsHandler, pathParams) : null;
		}

		Node current = root;
		int start = 0;
		for (int i = 0; i <= path.length; i++) {
			if (i == path.length || path[i] == '/') {
				if (i > start) {
					current = findNextForWs(current, path, start, i, pathParams);
					if (current == null)
						return null;
				}
				start = i + 1;
			}
		}

		return current.wsHandler != null ? new WsMatch(current.wsHandler, pathParams) : null;
	}

	private Node findNextForWs(Node current, byte[] path, int start, int end, Map<String, String> pathParams) {
		for (Node child : current.staticChildren.values()) {
			if (bytesEqual(child.nameBytes, path, start, end)) {
				return child;
			}
		}
		if (current.paramChild != null) {
			String raw = new String(path, start, end - start, java.nio.charset.StandardCharsets.UTF_8);
			String paramValue = java.net.URLDecoder.decode(raw, java.nio.charset.StandardCharsets.UTF_8);
			pathParams.put(current.paramName, paramValue);
			return current.paramChild;
		}
		return null;
	}

	public static class WsMatch {
		public final summer.web.websocket.WebSocketHandler handler;
		public final Map<String, String> pathParams;
		public WsMatch(summer.web.websocket.WebSocketHandler handler, Map<String, String> pathParams) {
			this.handler = handler;
			this.pathParams = pathParams;
		}
	}

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
		Map<String, Handler> handlers = new HashMap<>(); // HTTP Method -> Handler
		summer.web.websocket.WebSocketHandler wsHandler = null;

		Node() {
			this.nameBytes = new byte[0];
		}
		Node(String name) {
			this.nameBytes = name.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		}
	}
}
