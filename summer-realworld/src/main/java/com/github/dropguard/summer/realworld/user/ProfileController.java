package com.github.dropguard.summer.realworld.user;

import java.util.Optional;

import com.github.dropguard.summer.realworld.user.UserDtos;
import com.github.dropguard.summer.realworld.user.User;
import com.github.dropguard.summer.realworld.user.FollowRepository;
import com.github.dropguard.summer.realworld.user.UserService;
import com.github.dropguard.summer.realworld.auth.AuthUtils;
import com.github.dropguard.summer.realworld.common.Errors;
import com.github.dropguard.summer.realworld.auth.JwtUtil;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.annotation.Delete;
import com.github.dropguard.summer.web.annotation.Get;
import com.github.dropguard.summer.web.annotation.PathParam;
import com.github.dropguard.summer.web.annotation.Post;
import com.github.dropguard.summer.web.annotation.RestController;

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

	private UserDtos.ProfileResponse createProfileResponse(User user, Long currentUserId) {
		boolean following = currentUserId != null && followRepository.isFollowing(currentUserId, user.getId());
		return new UserDtos.ProfileResponse(
				new UserDtos.ProfileResponse.Profile(user.getUsername(), user.getBio(), user.getImage(), following));
	}
}
