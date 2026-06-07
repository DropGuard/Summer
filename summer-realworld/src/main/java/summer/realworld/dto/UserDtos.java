package summer.realworld.dto;

import java.util.List;
import java.util.Map;

public class UserDtos {

	public record RegisterRequest(User user) {
		public record User(String username, String email, String password) {
		}
	}

	public record LoginRequest(User user) {
		public record User(String email, String password) {
		}
	}

	public record UpdateUserRequest(User user) {
		public record User(String username, String email, String password, String bio, String image) {
		}
	}

	public record UserResponse(User user) {
		public record User(String email, String token, String username, String bio, String image) {
		}
	}

	public record ErrorResponse(Map<String, List<String>> errors) {
		public static ErrorResponse of(String field, String message) {
			return new ErrorResponse(Map.of(field, List.of(message)));
		}
	}
}
