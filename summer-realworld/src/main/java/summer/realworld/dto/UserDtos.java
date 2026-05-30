package summer.realworld.dto;

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
}
