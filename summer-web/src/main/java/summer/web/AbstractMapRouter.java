package summer.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base class for Map-based routers (HTTP and WebSocket).
 *
 * <p>
 * Provides shared path normalization, pattern compilation, and matching logic.
 * Subclasses define the handler type and dispatch mechanism.
 * </p>
 */
public abstract class AbstractMapRouter {

	protected String normalizePath(String path) {
		return PathUtils.normalizePath(path);
	}

	protected RouteEntry parsePath(String path) {
		RouteEntry entry = new RouteEntry();
		String normalized = normalizePath(path);

		StringBuilder regex = new StringBuilder();
		regex.append("^/?");
		String[] segments = normalized.split("/");
		for (int i = 0; i < segments.length; i++) {
			String segment = segments[i];
			if (segment.isEmpty())
				continue;
			if (i > 0 || regex.length() > 2) {
				regex.append("/");
			}
			if ("**".equals(segment)) {
				regex.append("(.*)");
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

	protected Map<String, String> matchPattern(RouteEntry entry, String path) {
		Matcher matcher = entry.pattern.matcher(path);
		if (!matcher.matches()) {
			return null;
		}

		Map<String, String> params = new HashMap<>();
		for (int i = 0; i < entry.paramNames.size(); i++) {
			String raw = matcher.group(i + 1);
			String decoded = java.net.URLDecoder.decode(raw, java.nio.charset.StandardCharsets.UTF_8);
			params.put(entry.paramNames.get(i), decoded);
		}
		return params;
	}

	public static class RouteEntry {
		public java.util.regex.Pattern pattern;
		public List<String> paramNames = new ArrayList<>();
	}
}
