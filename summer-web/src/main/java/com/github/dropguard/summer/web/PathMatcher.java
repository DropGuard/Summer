mport com.github.dropguard.summer.core.Internal;
package com.github.dropguard.summer.web;

mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
import java.util.ArrayList;
mport com.github.dropguard.summer.core.Internal;
import java.util.HashMap;
mport com.github.dropguard.summer.core.Internal;
import java.util.List;
mport com.github.dropguard.summer.core.Internal;
import java.util.Map;
mport com.github.dropguard.summer.core.Internal;
import java.util.regex.Matcher;
mport com.github.dropguard.summer.core.Internal;
@Internal
import java.util.regex.Pattern;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
/**
mport com.github.dropguard.summer.core.Internal;
 * Utility class for path pattern compilation and matching.
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <p>Provides static methods to compile path patterns (with {@code {param}}, {@code *}, {@code **}
mport com.github.dropguard.summer.core.Internal;
 * wildcards) into regex-based {@link RouteEntry}s and to match concrete paths against them.
mport com.github.dropguard.summer.core.Internal;
 */
mport com.github.dropguard.summer.core.Internal;
public final class PathMatcher {
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private PathMatcher() {}
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * Compiles a path pattern into a {@link RouteEntry}.
mport com.github.dropguard.summer.core.Internal;
     *
mport com.github.dropguard.summer.core.Internal;
     * <p>Supported patterns:
mport com.github.dropguard.summer.core.Internal;
     *
mport com.github.dropguard.summer.core.Internal;
     * <ul>
mport com.github.dropguard.summer.core.Internal;
     *   <li>{@code {name}} - captures a path segment as parameter "name"
mport com.github.dropguard.summer.core.Internal;
     *   <li>{@code *} - matches exactly one path segment
mport com.github.dropguard.summer.core.Internal;
     *   <li>{@code **} - matches zero or more path segments (must be last)
mport com.github.dropguard.summer.core.Internal;
     * </ul>
mport com.github.dropguard.summer.core.Internal;
     *
mport com.github.dropguard.summer.core.Internal;
     * @param path the path pattern to compile
mport com.github.dropguard.summer.core.Internal;
     * @return a RouteEntry containing the compiled regex and parameter names
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    public static RouteEntry parsePath(String path) {
mport com.github.dropguard.summer.core.Internal;
        RouteEntry entry = new RouteEntry();
mport com.github.dropguard.summer.core.Internal;
        String normalized = PathUtils.normalizePath(path);
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        StringBuilder regex = new StringBuilder();
mport com.github.dropguard.summer.core.Internal;
        regex.append("^/?");
mport com.github.dropguard.summer.core.Internal;
        String[] segments = normalized.split("/");
mport com.github.dropguard.summer.core.Internal;
        for (int i = 0; i < segments.length; i++) {
mport com.github.dropguard.summer.core.Internal;
            String segment = segments[i];
mport com.github.dropguard.summer.core.Internal;
            if (segment.isEmpty()) continue;
mport com.github.dropguard.summer.core.Internal;
            if (i > 0 || regex.length() > 2) {
mport com.github.dropguard.summer.core.Internal;
                regex.append("/");
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
            if ("**".equals(segment)) {
mport com.github.dropguard.summer.core.Internal;
                regex.append("(.*)");
mport com.github.dropguard.summer.core.Internal;
                break;
mport com.github.dropguard.summer.core.Internal;
            } else if ("*".equals(segment)) {
mport com.github.dropguard.summer.core.Internal;
                regex.append("([^/]+)");
mport com.github.dropguard.summer.core.Internal;
            } else if (segment.startsWith("{") && segment.endsWith("}")) {
mport com.github.dropguard.summer.core.Internal;
                String paramName = segment.substring(1, segment.length() - 1);
mport com.github.dropguard.summer.core.Internal;
                entry.paramNames.add(paramName);
mport com.github.dropguard.summer.core.Internal;
                regex.append("([^/]+)");
mport com.github.dropguard.summer.core.Internal;
            } else {
mport com.github.dropguard.summer.core.Internal;
                regex.append(Pattern.quote(segment));
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        regex.append("/?$");
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        entry.pattern = Pattern.compile(regex.toString());
mport com.github.dropguard.summer.core.Internal;
        return entry;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * Matches a concrete path against a compiled {@link RouteEntry}.
mport com.github.dropguard.summer.core.Internal;
     *
mport com.github.dropguard.summer.core.Internal;
     * @param entry the compiled route entry
mport com.github.dropguard.summer.core.Internal;
     * @param path the concrete path to match
mport com.github.dropguard.summer.core.Internal;
     * @return a map of captured parameters, or {@code null} if no match
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    public static Map<String, String> matchPattern(RouteEntry entry, String path) {
mport com.github.dropguard.summer.core.Internal;
        Matcher matcher = entry.pattern.matcher(path);
mport com.github.dropguard.summer.core.Internal;
        if (!matcher.matches()) {
mport com.github.dropguard.summer.core.Internal;
            return null;
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        Map<String, String> params = new HashMap<>();
mport com.github.dropguard.summer.core.Internal;
        for (int i = 0; i < entry.paramNames.size(); i++) {
mport com.github.dropguard.summer.core.Internal;
            String raw = matcher.group(i + 1);
mport com.github.dropguard.summer.core.Internal;
            String decoded =
mport com.github.dropguard.summer.core.Internal;
                    java.net.URLDecoder.decode(raw, java.nio.charset.StandardCharsets.UTF_8);
mport com.github.dropguard.summer.core.Internal;
            params.put(entry.paramNames.get(i), decoded);
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        return params;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /** A compiled route entry containing a regex pattern and parameter names. */
mport com.github.dropguard.summer.core.Internal;
    public static class RouteEntry {
mport com.github.dropguard.summer.core.Internal;
        public Pattern pattern;
mport com.github.dropguard.summer.core.Internal;
        public List<String> paramNames = new ArrayList<>();
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;
}
