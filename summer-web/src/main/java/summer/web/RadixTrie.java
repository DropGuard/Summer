package summer.web;

import java.util.HashMap;
import java.util.Map;

/**
 * Generic Radix Tree (Trie) for high-performance path matching.
 *
 * <p>
 * Path segment matching is performed at the byte level to minimize String
 * allocations. Path parameter extraction still requires String creation.
 * </p>
 *
 * <p>
 * Supports path parameters ({@code {name}}), single-segment wildcards
 * ({@code *}), and multi-segment wildcards ({@code **}).
 * </p>
 *
 * @param <T>
 *            the type of handler stored at each node
 */
public class RadixTrie<T> {

	private final Node<T> root = new Node<>();

	/**
	 * Inserts a handler for the given path pattern.
	 *
	 * @param path
	 *            the path pattern (e.g., "/users/{id}")
	 * @param handler
	 *            the handler to store
	 */
	public void insert(String path, T handler) {
		navigateToNode(PathUtils.normalizePath(path)).handler = handler;
	}

	/**
	 * Retrieves the handler stored at the given path (without matching).
	 *
	 * @param path
	 *            the path to look up
	 * @return the handler, or null if not found
	 */
	public T get(String path) {
		Node<T> node = findNode(PathUtils.normalizePath(path));
		return node != null ? node.handler : null;
	}

	/**
	 * Matches a request path against the trie.
	 *
	 * @param path
	 *            the raw path bytes
	 * @return the match result containing handler and path parameters, or null
	 */
	public MatchResult<T> match(byte[] path) {
		if (isRootPath(path)) {
			return root.handler != null ? new MatchResult<>(root.handler, Map.of()) : null;
		}

		Map<String, String> params = new HashMap<>();
		Node<T> current = matchPath(path, params);
		if (current == null || current.handler == null) {
			return null;
		}

		return new MatchResult<>(current.handler, params);
	}

	private Node<T> findNode(String path) {
		String[] segments = tokenize(path);
		Node<T> current = root;

		for (String segment : segments) {
			if ("**".equals(segment)) {
				current = current.catchAllChild;
			} else if ("*".equals(segment)) {
				current = current.wildcardChild;
			} else if (segment.startsWith("{") && segment.endsWith("}")) {
				current = current.paramChild;
			} else {
				current = current.staticChildren.get(segment);
			}
			if (current == null) {
				return null;
			}
		}
		return current;
	}

	private Node<T> navigateToNode(String path) {
		String[] segments = tokenize(path);
		Node<T> current = root;

		for (String segment : segments) {
			current = navigateSegment(current, segment, path);
		}
		return current;
	}

	private Node<T> navigateSegment(Node<T> current, String segment, String path) {
		if ("**".equals(segment)) {
			if (current.catchAllChild == null) {
				current.catchAllChild = new Node<>();
			}
			return current.catchAllChild;
		} else if ("*".equals(segment)) {
			if (current.wildcardChild == null) {
				current.wildcardChild = new Node<>();
			}
			return current.wildcardChild;
		} else if (segment.startsWith("{") && segment.endsWith("}")) {
			String paramName = segment.substring(1, segment.length() - 1);
			if (current.paramChild != null && !current.paramName.equals(paramName)) {
				throw new summer.web.exception.RouteConflictException(path);
			}
			if (current.paramChild == null) {
				current.paramChild = new Node<>();
				current.paramName = paramName;
			}
			return current.paramChild;
		} else {
			return current.staticChildren.computeIfAbsent(segment, k -> new Node<>(k));
		}
	}

	private boolean isRootPath(byte[] path) {
		return path == null || path.length == 0 || (path.length == 1 && path[0] == '/');
	}

	private Node<T> matchPath(byte[] path, Map<String, String> params) {
		Node<T> current = root;
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
		if (current.catchAllChild != null && current.handler == null) {
			current = current.catchAllChild;
		}
		return current;
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
	private Node<T> findNext(Node<T> current, byte[] path, int start, int end, Map<String, String> params) {
		// Try static children first --linear scan is faster than HashMap for small N
		for (Node<T> child : current.staticChildren.values()) {
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

	private static class Node<T> {
		final byte[] nameBytes;
		Map<String, Node<T>> staticChildren = new HashMap<>();
		Node<T> paramChild = null;
		String paramName = null;
		Node<T> wildcardChild = null; // * matches any single segment
		Node<T> catchAllChild = null; // ** matches rest of path
		T handler = null;

		Node() {
			this.nameBytes = new byte[0];
		}

		Node(String name) {
			this.nameBytes = name.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		}
	}

	/**
	 * Result of matching a path against the trie.
	 *
	 * @param handler
	 *            the matched handler
	 * @param params
	 *            the extracted path parameters
	 * @param <T>
	 *            the handler type
	 */
	public record MatchResult<T>(T handler, Map<String, String> params) {
	}
}
