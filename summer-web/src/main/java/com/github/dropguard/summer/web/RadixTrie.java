package com.github.dropguard.summer.web;

import com.github.dropguard.summer.core.Internal;
import java.util.HashMap;
import java.util.Map;

/**
 * Generic Radix Tree (Trie) for high-performance path matching.
 *
 * <p>Path segment matching is performed at the byte level to minimize String allocations. Path
 * parameter extraction still requires String creation.
 *
 * <p>Supports path parameters ({@code {name}}), single-segment wildcards ({@code *}), and
 * multi-segment wildcards ({@code **}).
 *
 * <h2>Matching priority (shared contract)</h2>
 *
 * <p>Both routers in the framework (this Radix trie and the Map-based router) honour the same
 * priority order, so the same route table resolves identically regardless of {@code
 * server.router-type}:
 *
 * <ol>
 *   <li>Exact static segment
 *   <li>Path parameter ({@code {name}})
 *   <li>Single-segment wildcard ({@code *})
 *   <li>Multi-segment wildcard ({@code **}) — <em>last resort</em>, only when no more-specific
 *       segment matches
 * </ol>
 *
 * A catch-all ({@code **}) never shadows a more specific sibling: {@code /api/users} beats {@code
 * /api/**}, but {@code /api/users/42} still falls through to {@code /api/**} because the more
 * specific branches dead-end.
 *
 * @param <T> the type of handler stored at each node
 */
@Internal
public class RadixTrie<T> {

    private final Node<T> root = new Node<>();

    /**
     * Inserts a handler for the given path pattern.
     *
     * @param path the path pattern (e.g., "/users/{id}")
     * @param handler the handler to store
     */
    public void insert(String path, T handler) {
        navigateToNode(PathUtils.normalizePath(path)).handler = handler;
    }

    /**
     * Retrieves the handler stored at the given path (without matching).
     *
     * @param path the path to look up
     * @return the handler, or null if not found
     */
    public T get(String path) {
        Node<T> node = findNode(PathUtils.normalizePath(path));
        return node != null ? node.handler : null;
    }

    /**
     * Matches a request path against the trie.
     *
     * @param path the raw path bytes
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
                throw new com.github.dropguard.summer.web.exception.RouteConflictException(path);
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

    /**
     * Matches the path against the trie, honouring the documented priority order (exact static &gt;
     * path parameter &gt; single-segment wildcard ({@code *}) &gt; multi-segment wildcard ({@code
     * **})).
     *
     * <p>A {@code **} node is a <em>last-resort</em> fallback: it is only entered when no
     * more-specific child (static / param / {@code *}) matches. Because descending into a specific
     * child can later dead-end, we remember the nearest ancestor catch-all (with a handler) as a
     * {@code fallback}; if a segment has no specific child, we backtrack to that fallback (and undo
     * any path parameter that specific descent bound). This is what keeps the two routers (Radix
     * and Map) behaviourally identical — e.g. {@code /api/users} beats {@code /api/**}, yet {@code
     * /api/users/42} still falls through to {@code /api/**}.
     */
    private Node<T> matchPath(byte[] path, Map<String, String> params) {
        Node<T> current = root;
        Node<T> fallback = null; // nearest ancestor catch-all whose handler is non-null
        int start = 0;
        for (int i = 0; i <= path.length; i++) {
            if (i == path.length || path[i] == '/') {
                if (i > start) {
                    // A catch-all at the current node (with a handler) is a viable fallback for
                    // any deeper segment that fails to match specifically.
                    if (current.catchAllChild != null && current.catchAllChild.handler != null) {
                        fallback = current.catchAllChild;
                    }
                    NodeAndParam next = findNext(current, path, start, i, params);
                    if (next.node() == null) {
                        // Dead end on a specific child — undo the parameter this descent bound
                        // and backtrack to the nearest catch-all.
                        if (next.boundParam() != null) {
                            params.remove(next.boundParam());
                        }
                        return fallback;
                    }
                    current = next.node();
                }
                start = i + 1;
            }
        }

        // Path fully consumed by specific segments.
        if (current.handler != null) {
            return current;
        }
        if (current.catchAllChild != null && current.catchAllChild.handler != null) {
            return current.catchAllChild;
        }
        return fallback;
    }

    /**
     * Finds the next node in the trie for the given path segment, honouring the priority order
     * (static &gt; path parameter &gt; single-segment wildcard). The returned record also reports
     * which path parameter (if any) was bound, so a later dead-end can undo that binding before
     * backtracking to a catch-all.
     */
    private NodeAndParam<T> findNext(
            Node<T> current, byte[] path, int start, int end, Map<String, String> params) {
        // Try static children first --linear scan is faster than HashMap for small N
        for (Node<T> child : current.staticChildren.values()) {
            if (bytesEqual(child.nameBytes, path, start, end)) {
                return new NodeAndParam<>(child, null);
            }
        }
        // Fall back to parameterized child (e.g., {id})
        if (current.paramChild != null) {
            String raw =
                    new String(path, start, end - start, java.nio.charset.StandardCharsets.UTF_8);
            String paramValue =
                    java.net.URLDecoder.decode(raw, java.nio.charset.StandardCharsets.UTF_8);
            params.put(current.paramName, paramValue);
            return new NodeAndParam<>(current.paramChild, current.paramName);
        }
        // Fall back to single-segment wildcard (*)
        if (current.wildcardChild != null) {
            return new NodeAndParam<>(current.wildcardChild, null);
        }
        return new NodeAndParam<>(null, null);
    }

    /**
     * Compares a node's name bytes against a segment of the path bytes. This avoids creating String
     * objects for comparison.
     */
    private boolean bytesEqual(byte[] segment, byte[] path, int start, int end) {
        if (segment.length != (end - start)) return false;
        for (int i = 0; i < segment.length; i++) {
            if (segment[i] != path[start + i]) return false;
        }
        return true;
    }

    private String[] tokenize(String path) {
        if (path == null || path.equals("/") || path.isEmpty()) {
            return new String[0];
        }
        return java.util.Arrays.stream(path.split("/"))
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
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
     * @param handler the matched handler
     * @param params the extracted path parameters
     * @param <T> the handler type
     */
    public record MatchResult<T>(T handler, Map<String, String> params) {}

    /**
     * Internal result of {@link #findNext}: the next node (or null on no specific match) plus the
     * name of the path parameter bound by that descent (or null when no parameter was bound).
     *
     * @param node the matched child node, or null
     * @param boundParam the parameter name bound during this descent, or null
     * @param <T> the handler type
     */
    private record NodeAndParam<T>(Node<T> node, String boundParam) {}
}
