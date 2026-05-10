package summer.web;

import java.util.HashMap;
import java.util.Map;
import summer.core.Component;

/**
 * High-performance router implementation using a Radix Tree (Trie) 
 * for O(L) path resolution where L is the path depth.
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
					throw new RuntimeException("Conflict: Parameter name mismatch at " + path);
				}
				if (current.paramChild == null) {
					current.paramChild = new Node();
					current.paramName = paramName;
				}
				current = current.paramChild;
			} else {
				current = current.staticChildren.computeIfAbsent(segment, k -> new Node());
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

	public Object route(WebContext ctx) {
		String[] segments = tokenize(ctx.request().getPath());
		Node current = root;

		for (String segment : segments) {
			Node next = current.staticChildren.get(segment);
			if (next != null) {
				current = next;
			} else if (current.paramChild != null) {
				// Match parameter
				ctx.request().setAttribute(current.paramName, segment);
				current = current.paramChild;
			} else {
				return null; // 404
			}
		}

		Handler handler = current.handlers.get(ctx.request().getMethod().toUpperCase());
		return handler != null ? handler.handle(ctx) : null;
	}

	private String[] tokenize(String path) {
		if (path == null || path.equals("/") || path.isEmpty()) {
			return new String[0];
		}
		// Split by / and remove empty segments
		return java.util.Arrays.stream(path.split("/"))
				.filter(s -> !s.isEmpty())
				.toArray(String[]::new);
	}

	private static class Node {
		Map<String, Node> staticChildren = new HashMap<>();
		Node paramChild = null;
		String paramName = null;
		Map<String, Handler> handlers = new HashMap<>(); // HTTP Method -> Handler
	}
}
