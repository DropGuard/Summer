mport com.github.dropguard.summer.core.Internal;
package com.github.dropguard.summer.web;

mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
import java.util.HashMap;
mport com.github.dropguard.summer.core.Internal;
import java.util.Map;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
/**
mport com.github.dropguard.summer.core.Internal;
 * Generic Radix Tree (Trie) for high-performance path matching.
mport com.github.dropguard.summer.core.Internal;
 *
@Internal
mport com.github.dropguard.summer.core.Internal;
 * <p>Path segment matching is performed at the byte level to minimize String allocations. Path
mport com.github.dropguard.summer.core.Internal;
 * parameter extraction still requires String creation.
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <p>Supports path parameters ({@code {name}}), single-segment wildcards ({@code *}), and
mport com.github.dropguard.summer.core.Internal;
 * multi-segment wildcards ({@code **}).
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * @param <T> the type of handler stored at each node
mport com.github.dropguard.summer.core.Internal;
 */
mport com.github.dropguard.summer.core.Internal;
public class RadixTrie<T> {
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private final Node<T> root = new Node<>();
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * Inserts a handler for the given path pattern.
mport com.github.dropguard.summer.core.Internal;
     *
mport com.github.dropguard.summer.core.Internal;
     * @param path the path pattern (e.g., "/users/{id}")
mport com.github.dropguard.summer.core.Internal;
     * @param handler the handler to store
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    public void insert(String path, T handler) {
mport com.github.dropguard.summer.core.Internal;
        navigateToNode(PathUtils.normalizePath(path)).handler = handler;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * Retrieves the handler stored at the given path (without matching).
mport com.github.dropguard.summer.core.Internal;
     *
mport com.github.dropguard.summer.core.Internal;
     * @param path the path to look up
mport com.github.dropguard.summer.core.Internal;
     * @return the handler, or null if not found
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    public T get(String path) {
mport com.github.dropguard.summer.core.Internal;
        Node<T> node = findNode(PathUtils.normalizePath(path));
mport com.github.dropguard.summer.core.Internal;
        return node != null ? node.handler : null;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * Matches a request path against the trie.
mport com.github.dropguard.summer.core.Internal;
     *
mport com.github.dropguard.summer.core.Internal;
     * @param path the raw path bytes
mport com.github.dropguard.summer.core.Internal;
     * @return the match result containing handler and path parameters, or null
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    public MatchResult<T> match(byte[] path) {
mport com.github.dropguard.summer.core.Internal;
        if (isRootPath(path)) {
mport com.github.dropguard.summer.core.Internal;
            return root.handler != null ? new MatchResult<>(root.handler, Map.of()) : null;
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        Map<String, String> params = new HashMap<>();
mport com.github.dropguard.summer.core.Internal;
        Node<T> current = matchPath(path, params);
mport com.github.dropguard.summer.core.Internal;
        if (current == null || current.handler == null) {
mport com.github.dropguard.summer.core.Internal;
            return null;
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        return new MatchResult<>(current.handler, params);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private Node<T> findNode(String path) {
mport com.github.dropguard.summer.core.Internal;
        String[] segments = tokenize(path);
mport com.github.dropguard.summer.core.Internal;
        Node<T> current = root;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        for (String segment : segments) {
mport com.github.dropguard.summer.core.Internal;
            if ("**".equals(segment)) {
mport com.github.dropguard.summer.core.Internal;
                current = current.catchAllChild;
mport com.github.dropguard.summer.core.Internal;
            } else if ("*".equals(segment)) {
mport com.github.dropguard.summer.core.Internal;
                current = current.wildcardChild;
mport com.github.dropguard.summer.core.Internal;
            } else if (segment.startsWith("{") && segment.endsWith("}")) {
mport com.github.dropguard.summer.core.Internal;
                current = current.paramChild;
mport com.github.dropguard.summer.core.Internal;
            } else {
mport com.github.dropguard.summer.core.Internal;
                current = current.staticChildren.get(segment);
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
            if (current == null) {
mport com.github.dropguard.summer.core.Internal;
                return null;
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        return current;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private Node<T> navigateToNode(String path) {
mport com.github.dropguard.summer.core.Internal;
        String[] segments = tokenize(path);
mport com.github.dropguard.summer.core.Internal;
        Node<T> current = root;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        for (String segment : segments) {
mport com.github.dropguard.summer.core.Internal;
            current = navigateSegment(current, segment, path);
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        return current;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private Node<T> navigateSegment(Node<T> current, String segment, String path) {
mport com.github.dropguard.summer.core.Internal;
        if ("**".equals(segment)) {
mport com.github.dropguard.summer.core.Internal;
            if (current.catchAllChild == null) {
mport com.github.dropguard.summer.core.Internal;
                current.catchAllChild = new Node<>();
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
            return current.catchAllChild;
mport com.github.dropguard.summer.core.Internal;
        } else if ("*".equals(segment)) {
mport com.github.dropguard.summer.core.Internal;
            if (current.wildcardChild == null) {
mport com.github.dropguard.summer.core.Internal;
                current.wildcardChild = new Node<>();
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
            return current.wildcardChild;
mport com.github.dropguard.summer.core.Internal;
        } else if (segment.startsWith("{") && segment.endsWith("}")) {
mport com.github.dropguard.summer.core.Internal;
            String paramName = segment.substring(1, segment.length() - 1);
mport com.github.dropguard.summer.core.Internal;
            if (current.paramChild != null && !current.paramName.equals(paramName)) {
mport com.github.dropguard.summer.core.Internal;
                throw new com.github.dropguard.summer.web.exception.RouteConflictException(path);
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
            if (current.paramChild == null) {
mport com.github.dropguard.summer.core.Internal;
                current.paramChild = new Node<>();
mport com.github.dropguard.summer.core.Internal;
                current.paramName = paramName;
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
            return current.paramChild;
mport com.github.dropguard.summer.core.Internal;
        } else {
mport com.github.dropguard.summer.core.Internal;
            return current.staticChildren.computeIfAbsent(segment, k -> new Node<>(k));
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private boolean isRootPath(byte[] path) {
mport com.github.dropguard.summer.core.Internal;
        return path == null || path.length == 0 || (path.length == 1 && path[0] == '/');
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private Node<T> matchPath(byte[] path, Map<String, String> params) {
mport com.github.dropguard.summer.core.Internal;
        Node<T> current = root;
mport com.github.dropguard.summer.core.Internal;
        int start = 0;
mport com.github.dropguard.summer.core.Internal;
        for (int i = 0; i <= path.length; i++) {
mport com.github.dropguard.summer.core.Internal;
            if (i == path.length || path[i] == '/') {
mport com.github.dropguard.summer.core.Internal;
                if (i > start) {
mport com.github.dropguard.summer.core.Internal;
                    if (current.catchAllChild != null) {
mport com.github.dropguard.summer.core.Internal;
                        current = current.catchAllChild;
mport com.github.dropguard.summer.core.Internal;
                        break;
mport com.github.dropguard.summer.core.Internal;
                    }
mport com.github.dropguard.summer.core.Internal;
                    current = findNext(current, path, start, i, params);
mport com.github.dropguard.summer.core.Internal;
                    if (current == null) {
mport com.github.dropguard.summer.core.Internal;
                        return null;
mport com.github.dropguard.summer.core.Internal;
                    }
mport com.github.dropguard.summer.core.Internal;
                }
mport com.github.dropguard.summer.core.Internal;
                start = i + 1;
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        // If we ended on a node with catchAllChild, use it
mport com.github.dropguard.summer.core.Internal;
        if (current.catchAllChild != null && current.handler == null) {
mport com.github.dropguard.summer.core.Internal;
            current = current.catchAllChild;
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        return current;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * Finds the next node in the trie for the given path segment.
mport com.github.dropguard.summer.core.Internal;
     *
mport com.github.dropguard.summer.core.Internal;
     * <p>Matching priority:
mport com.github.dropguard.summer.core.Internal;
     *
mport com.github.dropguard.summer.core.Internal;
     * <ol>
mport com.github.dropguard.summer.core.Internal;
     *   <li>Static children (exact match)
mport com.github.dropguard.summer.core.Internal;
     *   <li>Path parameter ({@code {name}})
mport com.github.dropguard.summer.core.Internal;
     *   <li>Single-segment wildcard ({@code *})
mport com.github.dropguard.summer.core.Internal;
     *   <li>Multi-segment wildcard ({@code **}) - handled by caller
mport com.github.dropguard.summer.core.Internal;
     * </ol>
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    private Node<T> findNext(
mport com.github.dropguard.summer.core.Internal;
            Node<T> current, byte[] path, int start, int end, Map<String, String> params) {
mport com.github.dropguard.summer.core.Internal;
        // Try static children first --linear scan is faster than HashMap for small N
mport com.github.dropguard.summer.core.Internal;
        for (Node<T> child : current.staticChildren.values()) {
mport com.github.dropguard.summer.core.Internal;
            if (bytesEqual(child.nameBytes, path, start, end)) {
mport com.github.dropguard.summer.core.Internal;
                return child;
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        // Fall back to parameterized child (e.g., {id})
mport com.github.dropguard.summer.core.Internal;
        if (current.paramChild != null) {
mport com.github.dropguard.summer.core.Internal;
            String raw =
mport com.github.dropguard.summer.core.Internal;
                    new String(path, start, end - start, java.nio.charset.StandardCharsets.UTF_8);
mport com.github.dropguard.summer.core.Internal;
            String paramValue =
mport com.github.dropguard.summer.core.Internal;
                    java.net.URLDecoder.decode(raw, java.nio.charset.StandardCharsets.UTF_8);
mport com.github.dropguard.summer.core.Internal;
            params.put(current.paramName, paramValue);
mport com.github.dropguard.summer.core.Internal;
            return current.paramChild;
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        // Fall back to single-segment wildcard (*)
mport com.github.dropguard.summer.core.Internal;
        if (current.wildcardChild != null) {
mport com.github.dropguard.summer.core.Internal;
            return current.wildcardChild;
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        return null;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * Compares a node's name bytes against a segment of the path bytes. This avoids creating String
mport com.github.dropguard.summer.core.Internal;
     * objects for comparison.
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    private boolean bytesEqual(byte[] segment, byte[] path, int start, int end) {
mport com.github.dropguard.summer.core.Internal;
        if (segment.length != (end - start)) return false;
mport com.github.dropguard.summer.core.Internal;
        for (int i = 0; i < segment.length; i++) {
mport com.github.dropguard.summer.core.Internal;
            if (segment[i] != path[start + i]) return false;
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        return true;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private String[] tokenize(String path) {
mport com.github.dropguard.summer.core.Internal;
        if (path == null || path.equals("/") || path.isEmpty()) {
mport com.github.dropguard.summer.core.Internal;
            return new String[0];
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        return java.util.Arrays.stream(path.split("/"))
mport com.github.dropguard.summer.core.Internal;
                .filter(s -> !s.isEmpty())
mport com.github.dropguard.summer.core.Internal;
                .toArray(String[]::new);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private static class Node<T> {
mport com.github.dropguard.summer.core.Internal;
        final byte[] nameBytes;
mport com.github.dropguard.summer.core.Internal;
        Map<String, Node<T>> staticChildren = new HashMap<>();
mport com.github.dropguard.summer.core.Internal;
        Node<T> paramChild = null;
mport com.github.dropguard.summer.core.Internal;
        String paramName = null;
mport com.github.dropguard.summer.core.Internal;
        Node<T> wildcardChild = null; // * matches any single segment
mport com.github.dropguard.summer.core.Internal;
        Node<T> catchAllChild = null; // ** matches rest of path
mport com.github.dropguard.summer.core.Internal;
        T handler = null;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        Node() {
mport com.github.dropguard.summer.core.Internal;
            this.nameBytes = new byte[0];
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        Node(String name) {
mport com.github.dropguard.summer.core.Internal;
            this.nameBytes = name.getBytes(java.nio.charset.StandardCharsets.UTF_8);
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * Result of matching a path against the trie.
mport com.github.dropguard.summer.core.Internal;
     *
mport com.github.dropguard.summer.core.Internal;
     * @param handler the matched handler
mport com.github.dropguard.summer.core.Internal;
     * @param params the extracted path parameters
mport com.github.dropguard.summer.core.Internal;
     * @param <T> the handler type
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    public record MatchResult<T>(T handler, Map<String, String> params) {}
mport com.github.dropguard.summer.core.Internal;
}
