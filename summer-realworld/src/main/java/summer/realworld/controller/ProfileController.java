package summer.realworld.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import summer.realworld.model.User;
import summer.realworld.repository.FollowRepository;
import summer.realworld.service.UserService;
import summer.realworld.util.Errors;
import summer.realworld.util.JwtUtil;
import summer.web.HttpContext;
import summer.web.HttpStatus;
import summer.web.annotation.Delete;
import summer.web.annotation.Get;
import summer.web.annotation.PathParam;
import summer.web.annotation.Post;
import summer.web.annotation.RestController;

@RestController("/api")
public class ProfileController {
	private final UserService userService;
	private final FollowRepository followRepository;

	public ProfileController(UserService userService, FollowRepository followRepository) {
		this.userService = userService;
		this.followRepository = followRepository;
	}

	@Get("/profiles/{username}")
	public void getProfile(HttpContext ctx, @PathParam("username") String username) {
		Optional<User> userOpt = userService.findByUsername(username);
		if (userOpt.isEmpty()) {
			ctx.json(HttpStatus.NOT_FOUND, Errors.profileNotFound());
			return;
		}

		Long currentUserId = getCurrentUserId(ctx);
		ctx.json(HttpStatus.OK, createProfileResponse(userOpt.get(), currentUserId));
	}

	@Post("/profiles/{username}/follow")
	public void followUser(HttpContext ctx, @PathParam("username") String username) {
		Long currentUserId = getCurrentUserId(ctx);
		if (currentUserId == null) {
			ctx.json(HttpStatus.UNAUTHORIZED, Errors.tokenMissing());
			return;
		}

		Optional<User> userOpt = userService.findByUsername(username);
		if (userOpt.isEmpty()) {
			ctx.json(HttpStatus.NOT_FOUND, Errors.profileNotFound());
			return;
		}

		followRepository.follow(currentUserId, userOpt.get().getId());
		ctx.json(HttpStatus.OK, createProfileResponse(userOpt.get(), currentUserId));
	}

	@Delete("/profiles/{username}/follow")
	public void unfollowUser(HttpContext ctx, @PathParam("username") String username) {
		Long currentUserId = getCurrentUserId(ctx);
		if (currentUserId == null) {
			ctx.json(HttpStatus.UNAUTHORIZED, Errors.tokenMissing());
			return;
		}

		Optional<User> userOpt = userService.findByUsername(username);
		if (userOpt.isEmpty()) {
			ctx.json(HttpStatus.NOT_FOUND, Errors.profileNotFound());
			return;
		}

		followRepository.unfollow(currentUserId, userOpt.get().getId());
		ctx.json(HttpStatus.OK, createProfileResponse(userOpt.get(), currentUserId));
	}

	private Map<String, Object> createProfileResponse(User user, Long currentUserId) {
		boolean following = currentUserId != null && followRepository.isFollowing(currentUserId, user.getId());

		Map<String, Object> profileResponse = new HashMap<>();
		profileResponse.put("username", user.getUsername());
		profileResponse.put("bio", user.getBio());
		profileResponse.put("image", user.getImage());
		profileResponse.put("following", following);

		return Map.of("profile", profileResponse);
	}

	private Long getCurrentUserId(HttpContext ctx) {
		String authHeader = ctx.header("Authorization");
		if (authHeader != null && authHeader.startsWith("Token ")) {
			try {
				return JwtUtil.getUserIdFromToken(authHeader.substring(6));
			} catch (Exception e) {
				return null;
			}
		}
		return null;
	}
}
