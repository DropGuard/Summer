package summer.web.websocket;

import java.util.HashMap;
import java.util.Map;
import summer.core.Component;
import summer.web.PathUtils;
import summer.web.WsRouter;

/**
 * WebSocket router implementation using a Radix Tree (Trie) for path matching.
 *
 * <p>Provides WebSocket-specific routing with support for path parameters
 * and wildcard patterns ({@code *} and {@code **}).</p>
 */
@Component
public class RadixWsRouter implements WsRouter {

	private final Node root = new Node();

	@Override
	public WsMatch routeWs(String pathStr) {
		byte[] path = pathStr.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		Map<String, String> params = new HashMap<>();
		if (isRootPath(path)) {
			return root.wsHandler != null ? new WsMatch(root.wsHandler, params) : null;
		}

		Node current = matchPath(path, params);
		if (current == null) {
			return null;
		}

		return current.wsHandler != null ? new WsMatch(current.wsHandler, params) : null;
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
					Node next = findNext(current, path, start, i, params);
					if (next != null) {
						current = next;
					} else if (current.catchAllChild != null) {
						current = current.catchAllChild;
						break;
					} else {
						return null;
					}
				}
				start = i + 1;
			}
		}
		return current;
	}

	@Override
	public void ws(String path, WebSocketHandler handler) {
		navigateToNode(PathUtils.normalizePath(path)).wsHandler = handler;
	}

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
				throw new IllegalArgumentException("Route conflict: " + path);
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

	private Node findNext(Node current, byte[] path, int start, int end, Map<String, String> params) {
		for (Node child : current.staticChildren.values()) {
			if (bytesEqual(child.nameBytes, path, start, end)) {
				return child;
			}
		}
		if (current.paramChild != null) {
			String raw = new String(path, start, end - start, java.nio.charset.StandardCharsets.UTF_8);
			String paramValue = java.net.URLDecoder.decode(raw, java.nio.charset.StandardCharsets.UTF_8);
			params.put(current.paramName, paramValue);
			return current.paramChild;
		}
		if (current.wildcardChild != null) {
			return current.wildcardChild;
		}
		return null;
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
		Node wildcardChild = null;
		Node catchAllChild = null;
		WebSocketHandler wsHandler = null;

		Node() {
			this.nameBytes = new byte[0];
		}
		Node(String name) {
			this.nameBytes = name.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		}
	}
}
