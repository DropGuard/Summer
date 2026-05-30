package summer.realworld.util;

import java.util.List;
import java.util.Map;

public final class Errors {

	private Errors() {
	}

	public static Map<String, Object> of(String field, String message) {
		return Map.of("errors", Map.of(field, List.of(message)));
	}

	public static Map<String, Object> tokenMissing() {
		return of("token", "is missing");
	}

	public static Map<String, Object> credentials() {
		return of("credentials", "invalid");
	}

	public static Map<String, Object> articleNotFound() {
		return of("article", "not found");
	}

	public static Map<String, Object> articleForbidden() {
		return of("article", "forbidden");
	}

	public static Map<String, Object> commentNotFound() {
		return of("comment", "not found");
	}

	public static Map<String, Object> commentForbidden() {
		return of("comment", "forbidden");
	}

	public static Map<String, Object> profileNotFound() {
		return of("profile", "not found");
	}
}
