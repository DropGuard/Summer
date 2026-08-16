package com.github.dropguard.summer.web;

import com.github.dropguard.summer.core.Internal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for path pattern compilation and matching.
 *
 * <p>Provides static methods to compile path patterns (with {@code {param}}, {@code *}, {@code **}
 * wildcards) into regex-based {@link RouteEntry}s and to match concrete paths against them.
 */
@Internal
public final class PathMatcher {

    private PathMatcher() {}

    /**
     * Compiles a path pattern into a {@link RouteEntry}.
     *
     * <p>Supported patterns:
     *
     * <ul>
     *   <li>{@code {name}} - captures a path segment as parameter "name"
     *   <li>{@code *} - matches exactly one path segment
     *   <li>{@code **} - matches zero or more path segments (must be last)
     * </ul>
     *
     * @param path the path pattern to compile
     * @return a RouteEntry containing the compiled regex and parameter names
     */
    public static RouteEntry parsePath(String path) {
        RouteEntry entry = new RouteEntry();
        String normalized = PathUtils.normalizePath(path);

        StringBuilder regex = new StringBuilder();
        regex.append("^/?");
        String[] segments = normalized.split("/");
        boolean firstSegment = true;
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            if (segment.isEmpty()) continue;
            if (!firstSegment) {
                regex.append("/");
            }
            firstSegment = false;
            if ("**".equals(segment)) {
                // Match zero or more path segments. Drop the "/" appended for this segment (the
                // previous segment's terminator), so a bare prefix matches: "/s/**" accepts "/s"
                // (zero segments after "s"), "/s/x" (one), etc.
                if (regex.length() > 0 && regex.charAt(regex.length() - 1) == '/') {
                    regex.setLength(regex.length() - 1);
                }
                regex.append("(?:/.*)?");
                entry.catchAll = true;
                break;
            } else if ("*".equals(segment)) {
                regex.append("([^/]+)");
            } else if (segment.startsWith("{") && segment.endsWith("}")) {
                String paramName = segment.substring(1, segment.length() - 1);
                entry.paramNames.add(paramName);
                regex.append("([^/]+)");
            } else {
                regex.append(Pattern.quote(segment));
            }
        }
        regex.append("/?$");

        entry.pattern = Pattern.compile(regex.toString());
        return entry;
    }

    /**
     * Matches a concrete path against a compiled {@link RouteEntry}.
     *
     * @param entry the compiled route entry
     * @param path the concrete path to match
     * @return a map of captured parameters, or {@code null} if no match
     */
    public static Map<String, String> matchPattern(RouteEntry entry, String path) {
        Matcher matcher = entry.pattern.matcher(path);
        if (!matcher.matches()) {
            return null;
        }

        Map<String, String> params = new HashMap<>();
        for (int i = 0; i < entry.paramNames.size(); i++) {
            String raw = matcher.group(i + 1);
            String decoded =
                    java.net.URLDecoder.decode(raw, java.nio.charset.StandardCharsets.UTF_8);
            params.put(entry.paramNames.get(i), decoded);
        }
        return params;
    }

    /** A compiled route entry containing a regex pattern and parameter names. */
    public static class RouteEntry {
        public Pattern pattern;
        public List<String> paramNames = new ArrayList<>();

        /** True when the pattern contains a multi-segment wildcard {@code **}. */
        public boolean catchAll = false;
    }
}
