package com.github.dropguard.summer.web;

import com.github.dropguard.summer.core.ErrorCode;
import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.web.exception.SummerWebException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
     * <p>Duplicate registration is rejected at insert time — the second handler used to silently
     * overwrite the first, surfacing as "wrong controller answered" in production with no
     * diagnostic. Fail fast instead, consistent with route-merge validation.
     *
     * @param path the path pattern (e.g., "/users/{id}")
     * @param handler the handler to store
     * @throws com.github.dropguard.summer.web.exception.RouteConflictException if a handler is
     *     already registered for this exact pattern
     */
    public void insert(String path, T handler) {
        Node<T> node = navigateToNode(PathUtils.normalizePath(path));
        if (node.handler != null) {
            throw com.github.dropguard.summer.web.exception.RouteConflictException.duplicate(
                    PathUtils.normalizePath(path));
        }
        node.handler = handler;
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
            if (root.handler != null) {
                return new MatchResult<>(root.handler, Collections.emptyMap());
            }
            if (root.catchAllChild != null && root.catchAllChild.handler != null) {
                return new MatchResult<>(root.catchAllChild.handler, Collections.emptyMap());
            }
            return null;
        }

        NodeAndParam<T> result = matchPath(path);
        if (result.node() == null || result.node().handler == null) {
            return null;
        }

        return new MatchResult<>(result.node().handler, materialize(result.bindings(), path));
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
        for (int i = 0; i < segments.length; i++) {
            if ("**".equals(segments[i]) && i < segments.length - 1) {
                throw new IllegalArgumentException(
                        "Multi-segment wildcard (**) must only appear as the final segment: "
                                + path);
            }
        }
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
     * Matches the path against the trie by recursive descent, honouring the documented priority
     * order (exact static &gt; path parameter &gt; single-segment wildcard ({@code *}) &gt;
     * multi-segment wildcard ({@code **})).
     *
     * <p>Priority applies at EVERY depth and the descent backtracks: a static child that matches
     * one segment but dead-ends deeper does not shadow a param/wildcard sibling one level up — with
     * {@code /files/{name}} and {@code /files/static/*} registered, a request for {@code
     * /files/static/a} still falls through to {@code /files/{name}} handling the two-segment
     * remainder? No — {@code /files/{name}} only covers one segment; the point is the reverse: a
     * request for {@code /files/static} (static branch present, but its subtree dead-ends) falls
     * through to the {@code /files/{name}} sibling. Catch-alls ({@code **}) remain the last resort:
     * entered only after static/param/{@code *} fail at the same node.
     *
     * <p>Bindings are collected as raw slices and only decoded into the params map once a handler
     * is actually reached (deferred materialisation): failed attempts pay no decoding cost, and an
     * invalid percent-escape surfaces as a typed 400-mapped exception instead of leaking out of the
     * matcher. Backtracking truncates the binding trail, so bindings made inside a dead-ended
     * branch never leak into the returned match.
     */
    private NodeAndParam<T> matchPath(String path) {
        int len = path.length();
        int[] starts = new int[8];
        int[] ends = new int[8];
        int count = 0;
        int i = 0;
        while (i < len) {
            while (i < len && path.charAt(i) == '/') i++;
            if (i >= len) break;
            int start = i;
            while (i < len && path.charAt(i) != '/') i++;
            if (count == starts.length) {
                starts = java.util.Arrays.copyOf(starts, count * 2);
                ends = java.util.Arrays.copyOf(ends, count * 2);
            }
            starts[count] = start;
            ends[count] = i;
            count++;
        }
        List<Binding> bindings = new ArrayList<>();
        Node<T> matched = matchFrom(root, path, starts, ends, 0, count, bindings);
        return matched != null
                ? new NodeAndParam<>(matched, bindings)
                : new NodeAndParam<>(null, null);
    }

    /**
     * Recursive descent with backtracking. Returns the node carrying a handler, or {@code null}
     * when every branch from this node dead-ends; on {@code null} the caller's binding trail is
     * rolled back to its state at entry.
     */
    private Node<T> matchFrom(
            Node<T> node,
            String path,
            int[] starts,
            int[] ends,
            int idx,
            int count,
            List<Binding> bindings) {
        if (idx == count) {
            if (node.handler != null) {
                return node;
            }
            // ** matches zero or more segments: "/s/**" accepts "/s" itself.
            if (node.catchAllChild != null && node.catchAllChild.handler != null) {
                return node.catchAllChild;
            }
            return null;
        }
        int start = starts[idx];
        int end = ends[idx];

        // 1. Exact static segment (at most one static child can match).
        for (Node<T> child : node.staticChildren.values()) {
            if (matchesSegment(child.name, path, start, end)) {
                Node<T> r = matchFrom(child, path, starts, ends, idx + 1, count, bindings);
                if (r != null) {
                    return r;
                }
            }
        }
        // 2. Path parameter — bind the raw slice, roll back if the branch dead-ends.
        if (node.paramChild != null) {
            bindings.add(new Binding(node.paramName, start, end));
            Node<T> r = matchFrom(node.paramChild, path, starts, ends, idx + 1, count, bindings);
            if (r != null) {
                return r;
            }
            bindings.remove(bindings.size() - 1);
        }
        // 3. Single-segment wildcard (*).
        if (node.wildcardChild != null) {
            Node<T> r = matchFrom(node.wildcardChild, path, starts, ends, idx + 1, count, bindings);
            if (r != null) {
                return r;
            }
        }
        // 4. Multi-segment wildcard (**) — last resort, matches the remaining segments.
        if (node.catchAllChild != null && node.catchAllChild.handler != null) {
            return node.catchAllChild;
        }
        return null;
    }

    /** Finds and materialises path parameters for a successful match. */
    private static Map<String, String> materialize(List<Binding> bindings, String path) {
        if (bindings == null || bindings.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> params = new HashMap<>(4);
        for (Binding b : bindings) {
            params.put(b.name(), decodeSegment(path.substring(b.start(), b.end())));
        }
        return params;
    }

    /**
     * Percent-decodes one URI path segment per RFC 3986. Deliberately NOT {@link URLDecoder}: that
     * applies application/x-www-form-urlencoded rules where {@code '+'} means space — in a path,
     * {@code '+'} is a legal literal character (think base64 values or emails). Query-string
     * parsing elsewhere keeps form semantics; the asymmetry is intentional.
     *
     * @throws SummerWebException mapped to 400 when an escape sequence is malformed
     */
    static String decodeSegment(String raw) {
        if (raw.indexOf('%') < 0) {
            return raw;
        }
        byte[] bytes = raw.getBytes(StandardCharsets.UTF_8);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(bytes.length);
        for (int i = 0; i < bytes.length; i++) {
            byte c = bytes[i];
            if (c == '%') {
                if (i + 2 >= bytes.length) {
                    throw new SummerWebException(
                            ErrorCode.ROUTE_MATCH_ERROR,
                            HttpStatus.BAD_REQUEST,
                            "Invalid percent-encoding in request path segment: " + raw);
                }
                int hi = Character.digit((char) bytes[i + 1], 16);
                int lo = Character.digit((char) bytes[i + 2], 16);
                if (hi < 0 || lo < 0) {
                    throw new SummerWebException(
                            ErrorCode.ROUTE_MATCH_ERROR,
                            HttpStatus.BAD_REQUEST,
                            "Invalid percent-encoding in request path segment: " + raw);
                }
                out.write((hi << 4) | lo);
                i += 2;
            } else {
                out.write(c); // includes literal '+'
            }
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
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
     * A raw parameter binding recorded during matching: the parameter name plus the slice of the
     * request path carrying its (still percent-encoded) value.
     *
     * @param name the path parameter name
     * @param start start index of the raw value in the request path
     * @param end end index of the raw value in the request path
     */
    private record Binding(String name, int start, int end) {}

    /**
     * Internal result of a descent step: the next node (or null on no specific match) plus the
     * binding trail accumulated so far. Bindings are materialised into a map only on success — a
     * dead-ended branch's trail is simply discarded, which is what keeps catch-all fallbacks free
     * of debris from more-specific branches.
     *
     * @param node the matched child node, or null
     * @param bindings the binding trail, or null when nothing has been bound yet
     * @param <T> the handler type
     */
    private record NodeAndParam<T>(Node<T> node, List<Binding> bindings) {}
}
