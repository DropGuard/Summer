package com.github.dropguard.summer.realworld.user;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.mindrot.jbcrypt.BCrypt;

import com.github.dropguard.summer.realworld.user.UserDtos;
import com.github.dropguard.summer.realworld.user.User;
import com.github.dropguard.summer.realworld.user.UserService;
import com.github.dropguard.summer.realworld.auth.AuthUtils;
import com.github.dropguard.summer.realworld.common.Errors;
import com.github.dropguard.summer.realworld.auth.JwtUtil;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.annotation.Get;
import com.github.dropguard.summer.web.annotation.Post;
import com.github.dropguard.summer.web.annotation.Put;
import com.github.dropguard.summer.web.annotation.RestController;

@RestController("/api")
public class UserController {
	private final UserService userService;
	private final JwtUtil jwtUtil;

	public UserController(UserService userService, JwtUtil jwtUtil) {
		this.userService = userService;
		this.jwtUtil = jwtUtil;
	}

	@Post("/users")
	public void register(HttpContext ctx) {
		UserDtos.RegisterRequest body = ctx.validatedBody(UserDtos.RegisterRequest.class);
		var u = body.user();

		User user = userService.register(u.username(), u.email(), u.password());
		ctx.json(HttpStatus.CREATED, createUserResponse(user));
	}

	@Post("/users/login")
	public void login(HttpContext ctx) {
		UserDtos.LoginRequest body = ctx.validatedBody(UserDtos.LoginRequest.class);
		var u = body.user();

		Optional<User> userOpt = userService.findByEmail(u.email());
		if (userOpt.isEmpty() || !BCrypt.checkpw(u.password(), userOpt.get().getPassword())) {
			ctx.json(HttpStatus.UNAUTHORIZED, Errors.credentials());
			return;
		}

		ctx.json(HttpStatus.OK, createUserResponse(userOpt.get()));
	}

	@Get("/user")
	public void getCurrentUser(HttpContext ctx) {
		String token = extractToken(ctx);
		if (token == null || !jwtUtil.isAccessToken(token)) {
			ctx.json(HttpStatus.UNAUTHORIZED, Errors.tokenMissing());
			return;
		}

		Long userId = jwtUtil.getUserIdFromToken(token);
		Optional<User> userOpt = userService.findById(userId);
		if (userOpt.isEmpty()) {
			ctx.json(HttpStatus.UNAUTHORIZED, Errors.tokenMissing());
			return;
		}

		ctx.json(HttpStatus.OK, createUserResponse(userOpt.get()));
	}

	@Put("/user")
	public void updateUser(HttpContext ctx) {
		String token = extractToken(ctx);
		if (token == null || !jwtUtil.isAccessToken(token)) {
			ctx.json(HttpStatus.UNAUTHORIZED, Errors.tokenMissing());
			return;
		}

		Long userId = jwtUtil.getUserIdFromToken(token);
		Optional<User> userOpt = userService.findById(userId);
		if (userOpt.isEmpty()) {
			ctx.json(HttpStatus.UNAUTHORIZED, Errors.tokenMissing());
			return;
		}

		User user = userOpt.get();
		UserDtos.UpdateUserRequest body = ctx.body(UserDtos.UpdateUserRequest.class);
		var u = body.user();

		// Parse raw JSON to detect which fields are explicitly provided
		@SuppressWarnings("unchecked")
		Map<String, Object> rawBody = ctx.body(Map.class);
		Map<String, Object> rawUser = (Map<String, Object>) rawBody.get("user");

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

		User updatedUser = userService.update(user, u.username(), u.email(), u.password(), bio, image);
		ctx.json(HttpStatus.OK, createUserResponse(updatedUser));
	}

	private UserDtos.UserResponse createUserResponse(User user) {
		String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getUsername(), user.getEmail());
		return new UserDtos.UserResponse(
				new UserDtos.UserResponse.User(user.getEmail(), accessToken, user.getUsername(), user.getBio(), user.getImage()));
	}

	private String extractToken(HttpContext ctx) {
		return AuthUtils.extractToken(ctx);
	}
}
