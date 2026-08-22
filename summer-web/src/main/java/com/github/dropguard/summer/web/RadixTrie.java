package com.github.dropguard.summer.web;

import com.github.dropguard.summer.core.Internal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Generic Radix Tree (Trie) for high-performance path matching.
 *
 * <p>Path segment matching is performed directly on the path String with character-index slices to
 * eliminate heap allocations during routing.
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

    private final Node<T> root = new Node<>("");

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
     * Matches a request path against the trie using character slices (zero allocation).
     *
     * @param path the request path String
     * @return the match result containing handler and path parameters, or null
     */
    public MatchResult<T> match(String path) {
        if (isRootPath(path)) {
            return root.handler != null
                    ? new MatchResult<>(root.handler, Collections.emptyMap())
                    : null;
        }

        NodeAndParam<T> result = matchPath(path);
        if (result.node() == null || result.node().handler == null) {
            return null;
        }

        return new MatchResult<>(
                result.node().handler,
                result.params() != null ? result.params() : Collections.emptyMap());
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
                current.catchAllChild = new Node<>("**");
            }
            return current.catchAllChild;
        } else if ("*".equals(segment)) {
            if (current.wildcardChild == null) {
                current.wildcardChild = new Node<>("*");
            }
            return current.wildcardChild;
        } else if (segment.startsWith("{") && segment.endsWith("}")) {
            String paramName = segment.substring(1, segment.length() - 1);
            if (current.paramChild != null && !current.paramName.equals(paramName)) {
                throw new com.github.dropguard.summer.web.exception.RouteConflictException(path);
            }
            if (current.paramChild == null) {
                current.paramChild = new Node<>(segment);
                current.paramName = paramName;
            }
            return current.paramChild;
        } else {
            return current.staticChildren.computeIfAbsent(segment, Node::new);
        }
    }

    private boolean isRootPath(String path) {
        return path == null || path.isEmpty() || "/".equals(path);
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
     * any path parameter that specific descent bound).
     */
    private NodeAndParam<T> matchPath(String path) {
        Node<T> current = root;
        Node<T> fallback = null; // nearest ancestor catch-all whose handler is non-null
        Map<String, String> params = null;
        int start = 0;
        int len = path.length();
        for (int i = 0; i <= len; i++) {
            if (i == len || path.charAt(i) == '/') {
                if (i > start) {
                    // A catch-all at the current node (with a handler) is a viable fallback for
                    // any deeper segment that fails to match specifically.
                    if (current.catchAllChild != null && current.catchAllChild.handler != null) {
                        fallback = current.catchAllChild;
                    }
                    NodeAndParam<T> next = findNext(current, path, start, i, params);
                    if (next.node() == null) {
                        // Dead end on a specific child — undo the parameter this descent bound
                        // and backtrack to the nearest catch-all.
                        if (next.boundParam() != null && params != null) {
                            params.remove(next.boundParam());
                        }
                        return new NodeAndParam<>(fallback, null, params);
                    }
                    params = next.params();
                    current = next.node();
                }
                start = i + 1;
            }
        }

        // Path fully consumed by specific segments.
        if (current.handler != null) {
            return new NodeAndParam<>(current, null, params);
        }
        if (current.catchAllChild != null && current.catchAllChild.handler != null) {
            return new NodeAndParam<>(current.catchAllChild, null, params);
        }
        return new NodeAndParam<>(fallback, null, params);
    }

    /**
     * Finds the next node in the trie for the given path segment, honouring the priority order
     * (static &gt; path parameter &gt; single-segment wildcard). The returned record also reports
     * which path parameter (if any) was bound, so a later dead-end can undo that binding before
     * backtracking to a catch-all.
     */
    private NodeAndParam<T> findNext(
            Node<T> current, String path, int start, int end, Map<String, String> params) {
        // Try static children first -- linear scan with zero-allocation regionMatches
        for (Node<T> child : current.staticChildren.values()) {
            if (matchesSegment(child.name, path, start, end)) {
                return new NodeAndParam<>(child, null, params);
            }
        }
        // Fall back to parameterized child (e.g., {id})
        if (current.paramChild != null) {
            String raw = path.substring(start, end);
            String paramValue = URLDecoder.decode(raw, StandardCharsets.UTF_8);
            if (params == null) {
                params = new HashMap<>(4);
            }
            params.put(current.paramName, paramValue);
            return new NodeAndParam<>(current.paramChild, current.paramName, params);
        }
        // Fall back to single-segment wildcard (*)
        if (current.wildcardChild != null) {
            return new NodeAndParam<>(current.wildcardChild, null, params);
        }
        return new NodeAndParam<>(null, null, params);
    }

    /** Compares a node's name against a segment of the path without allocating substrings. */
    private boolean matchesSegment(String name, String path, int start, int end) {
        if (name.length() != (end - start)) return false;
        return path.regionMatches(start, name, 0, name.length());
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
        final String name;
        Map<String, Node<T>> staticChildren = new HashMap<>();
        Node<T> paramChild = null;
        String paramName = null;
        Node<T> wildcardChild = null; // * matches any single segment
        Node<T> catchAllChild = null; // ** matches rest of path
        T handler = null;

        Node(String name) {
            this.name = name;
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
     * name of the path parameter bound by that descent (or null when no parameter was bound) and
     * the parameter map.
     *
     * @param node the matched child node, or null
     * @param boundParam the parameter name bound during this descent, or null
     * @param params the lazily-created parameter map
     * @param <T> the handler type
     */
    private record NodeAndParam<T>(Node<T> node, String boundParam, Map<String, String> params) {}
}
