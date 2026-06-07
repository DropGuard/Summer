package summer.realworld.controller;

import java.util.Optional;

import summer.realworld.dto.UserDtos;
import summer.realworld.model.User;
import summer.realworld.repository.FollowRepository;
import summer.realworld.service.UserService;
import summer.realworld.util.AuthUtils;
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
	private final JwtUtil jwtUtil;

	public ProfileController(UserService userService, FollowRepository followRepository, JwtUtil jwtUtil) {
		this.userService = userService;
		this.followRepository = followRepository;
		this.jwtUtil = jwtUtil;
	}

	@Get("/profiles/{username}")
	public void getProfile(HttpContext ctx, @PathParam("username") String username) {
		Optional<User> userOpt = userService.findByUsername(username);
		if (userOpt.isEmpty()) {
			ctx.json(HttpStatus.NOT_FOUND, Errors.profileNotFound());
			return;
		}

		Long currentUserId = AuthUtils.getCurrentUserId(ctx, jwtUtil);
		ctx.json(HttpStatus.OK, createProfileResponse(userOpt.get(), currentUserId));
	}

	@Post("/profiles/{username}/follow")
	public void followUser(HttpContext ctx, @PathParam("username") String username) {
		Long currentUserId = AuthUtils.getCurrentUserId(ctx, jwtUtil);
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
		Long currentUserId = AuthUtils.getCurrentUserId(ctx, jwtUtil);
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

	private UserDtos.UserResponse createProfileResponse(User user, Long currentUserId) {
		boolean following = currentUserId != null && followRepository.isFollowing(currentUserId, user.getId());
		return new UserDtos.UserResponse(
				new UserDtos.UserResponse.User(user.getEmail(), null, user.getUsername(), user.getBio(), user.getImage()));
	}
}
