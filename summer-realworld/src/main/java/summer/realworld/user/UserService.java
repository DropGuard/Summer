package summer.realworld.user;

import java.time.LocalDateTime;
import java.util.Optional;
import org.mindrot.jbcrypt.BCrypt;
import summer.realworld.user.User;
import summer.realworld.user.UserRepository;

public class UserService {
	private final UserRepository userRepository;

	public UserService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	public User register(String username, String email, String password) {
		if (username == null || username.isBlank()) {
			throw new ValidationException("username", "can't be blank");
		}
		if (email == null || email.isBlank()) {
			throw new ValidationException("email", "can't be blank");
		}
		if (password == null || password.isBlank()) {
			throw new ValidationException("password", "can't be blank");
		}
		if (userRepository.findByUsername(username).isPresent()) {
			throw new ConflictException("username", "has already been taken");
		}
		if (userRepository.findByEmail(email).isPresent()) {
			throw new ConflictException("email", "has already been taken");
		}

		User user = new User();
		user.setUsername(username);
		user.setEmail(email);
		user.setPassword(BCrypt.hashpw(password, BCrypt.gensalt()));
		user.setCreatedAt(LocalDateTime.now());
		user.setUpdatedAt(LocalDateTime.now());
		return userRepository.save(user);
	}

	public Optional<User> findByEmail(String email) {
		return userRepository.findByEmail(email);
	}

	public Optional<User> findByUsername(String username) {
		return userRepository.findByUsername(username);
	}

	public Optional<User> findById(Long id) {
		return userRepository.findById(id);
	}

	public User update(User user, String username, String email, String password, String bio, String image) {
		if (username != null) {
			if (username.isBlank()) {
				throw new ValidationException("username", "can't be blank");
			}
			Optional<User> existing = userRepository.findByUsername(username);
			if (existing.isPresent() && !existing.get().getId().equals(user.getId())) {
				throw new ConflictException("username", "has already been taken");
			}
			user.setUsername(username);
		}
		if (email != null) {
			if (email.isBlank()) {
				throw new ValidationException("email", "can't be blank");
			}
			Optional<User> existing = userRepository.findByEmail(email);
			if (existing.isPresent() && !existing.get().getId().equals(user.getId())) {
				throw new ConflictException("email", "has already been taken");
			}
			user.setEmail(email);
		}
		if (password != null) {
			if (password.isBlank()) {
				throw new ValidationException("password", "can't be blank");
			}
			if (password.length() < 8) {
				throw new ValidationException("password", "is too short (minimum is 8 characters)");
			}
			user.setPassword(BCrypt.hashpw(password, BCrypt.gensalt()));
		}
		if (bio != null) {
			user.setBio(bio.isBlank() ? null : bio);
		}
		if (image != null) {
			user.setImage(image.isBlank() ? null : image);
		}
		user.setUpdatedAt(LocalDateTime.now());
		return userRepository.save(user);
	}

	public static class ValidationException extends RuntimeException {
		private final String field;
		private final String message;

		public ValidationException(String field, String message) {
			super(message);
			this.field = field;
			this.message = message;
		}

		public String getField() {
			return field;
		}
		public String getMessage() {
			return message;
		}
	}

	public static class ConflictException extends RuntimeException {
		private final String field;
		private final String message;

		public ConflictException(String field, String message) {
			super(message);
			this.field = field;
			this.message = message;
		}

		public String getField() {
			return field;
		}
		public String getMessage() {
			return message;
		}
	}
}
