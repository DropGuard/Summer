package summer.realworld.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.mindrot.jbcrypt.BCrypt;
import summer.realworld.dto.UserDtos;
import summer.realworld.model.User;
import summer.realworld.service.UserService;
import summer.realworld.util.Errors;
import summer.realworld.util.JwtUtil;
import summer.web.HttpStatus;
import summer.web.WebContext;
import summer.web.annotation.Get;
import summer.web.annotation.Post;
import summer.web.annotation.Put;
import summer.web.annotation.RestController;

@RestController("/api")
public class UserController {
	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@Post("/users")
	public void register(WebContext ctx) {
		UserDtos.RegisterRequest body = ctx.body(UserDtos.RegisterRequest.class);
		var u = body.user();

		try {
			User user = userService.register(u.username(), u.email(), u.password());
			ctx.json(HttpStatus.CREATED, createUserResponse(user));
		} catch (UserService.ValidationException e) {
			ctx.json(HttpStatus.UNPROCESSABLE_ENTITY, Errors.of(e.getField(), e.getMessage()));
		} catch (UserService.ConflictException e) {
			ctx.json(HttpStatus.CONFLICT, Errors.of(e.getField(), e.getMessage()));
		}
	}

	@Post("/users/login")
	public void login(WebContext ctx) {
		UserDtos.LoginRequest body = ctx.body(UserDtos.LoginRequest.class);
		var u = body.user();

		if (u.email() == null || u.email().isBlank()) {
			ctx.json(HttpStatus.UNPROCESSABLE_ENTITY, Errors.of("email", "can't be blank"));
			return;
		}
		if (u.password() == null || u.password().isBlank()) {
			ctx.json(HttpStatus.UNPROCESSABLE_ENTITY, Errors.of("password", "can't be blank"));
			return;
		}

		Optional<User> userOpt = userService.findByEmail(u.email());
		if (userOpt.isEmpty() || !BCrypt.checkpw(u.password(), userOpt.get().getPassword())) {
			ctx.json(HttpStatus.UNAUTHORIZED, Errors.credentials());
			return;
		}

		ctx.json(HttpStatus.OK, createUserResponse(userOpt.get()));
	}

	@Get("/user")
	public void getCurrentUser(WebContext ctx) {
		String token = extractToken(ctx);
		if (token == null) {
			ctx.json(HttpStatus.UNAUTHORIZED, Errors.tokenMissing());
			return;
		}

		Long userId = JwtUtil.getUserIdFromToken(token);
		Optional<User> userOpt = userService.findById(userId);
		if (userOpt.isEmpty()) {
			ctx.json(HttpStatus.UNAUTHORIZED, Errors.tokenMissing());
			return;
		}

		ctx.json(HttpStatus.OK, createUserResponse(userOpt.get()));
	}

	@Put("/user")
	public void updateUser(WebContext ctx) {
		String token = extractToken(ctx);
		if (token == null) {
			ctx.json(HttpStatus.UNAUTHORIZED, Errors.tokenMissing());
			return;
		}

		Long userId = JwtUtil.getUserIdFromToken(token);
		Optional<User> userOpt = userService.findById(userId);
		if (userOpt.isEmpty()) {
			ctx.json(HttpStatus.UNAUTHORIZED, Errors.tokenMissing());
			return;
		}

		User user = userOpt.get();
		UserDtos.UpdateUserRequest body = ctx.body(UserDtos.UpdateUserRequest.class);
		var u = body.user();

		// Parse raw JSON to detect which fields are explicitly provided
		Map<String, Object> rawUser = parseRawUserBody(ctx);

		// Reject null email/username (must be non-null if provided)
		if (rawUser != null && rawUser.containsKey("email") && rawUser.get("email") == null) {
			ctx.json(HttpStatus.UNPROCESSABLE_ENTITY, Errors.of("email", "can't be blank"));
			return;
		}
		if (rawUser != null && rawUser.containsKey("username") && rawUser.get("username") == null) {
			ctx.json(HttpStatus.UNPROCESSABLE_ENTITY, Errors.of("username", "can't be blank"));
			return;
		}
		// Reject null password
		if (rawUser != null && rawUser.containsKey("password") && rawUser.get("password") == null) {
			ctx.json(HttpStatus.UNPROCESSABLE_ENTITY, Errors.of("password", "can't be blank"));
			return;
		}

		// For bio/image: null in JSON means "set to null", absent means "don't update"
		boolean hasBio = rawUser != null && rawUser.containsKey("bio");
		boolean hasImage = rawUser != null && rawUser.containsKey("image");
		String bio = hasBio ? (u.bio() != null ? u.bio() : "") : null;
		String image = hasImage ? (u.image() != null ? u.image() : "") : null;

		try {
			User updatedUser = userService.update(user, u.username(), u.email(), u.password(), bio, image);
			ctx.json(HttpStatus.OK, createUserResponse(updatedUser));
		} catch (UserService.ValidationException e) {
			ctx.json(HttpStatus.UNPROCESSABLE_ENTITY, Errors.of(e.getField(), e.getMessage()));
		} catch (UserService.ConflictException e) {
			ctx.json(HttpStatus.CONFLICT, Errors.of(e.getField(), e.getMessage()));
		}
	}

	private Map<String, Object> createUserResponse(User user) {
		String accessToken = JwtUtil.generateAccessToken(user.getId(), user.getUsername(), user.getEmail());

		Map<String, Object> userResponse = new HashMap<>();
		userResponse.put("email", user.getEmail());
		userResponse.put("token", accessToken);
		userResponse.put("username", user.getUsername());
		userResponse.put("bio", user.getBio());
		userResponse.put("image", user.getImage());

		return Map.of("user", userResponse);
	}

	private String extractToken(WebContext ctx) {
		String authHeader = ctx.header("Authorization");
		if (authHeader != null && authHeader.startsWith("Token ")) {
			return authHeader.substring(6);
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> parseRawUserBody(WebContext ctx) {
		try {
			byte[] raw = ctx.request().getBody();
			if (raw == null || raw.length == 0)
				return null;
			Map<String, Object> root = new ObjectMapper().readValue(raw, Map.class);
			return (Map<String, Object>) root.get("user");
		} catch (Exception e) {
			return null;
		}
	}
}
