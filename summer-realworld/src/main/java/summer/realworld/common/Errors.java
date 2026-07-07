package summer.realworld.common;

import java.util.List;
import java.util.Map;

import summer.realworld.user.UserDtos.ErrorResponse;

public final class Errors {

	private Errors() {
	}

	public static ErrorResponse of(String field, String message) {
		return ErrorResponse.of(field, message);
	}

	public static ErrorResponse tokenMissing() {
		return of("token", "is missing");
	}

	public static ErrorResponse credentials() {
		return of("credentials", "invalid");
	}

	public static ErrorResponse articleNotFound() {
		return of("article", "not found");
	}

	public static ErrorResponse articleForbidden() {
		return of("article", "forbidden");
	}

	public static ErrorResponse commentNotFound() {
		return of("comment", "not found");
	}

	public static ErrorResponse commentForbidden() {
		return of("comment", "forbidden");
	}

	public static ErrorResponse profileNotFound() {
		return of("profile", "not found");
	}
}
