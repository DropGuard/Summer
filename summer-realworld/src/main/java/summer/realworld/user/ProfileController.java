package summer.realworld.user;

import java.util.Optional;

import summer.realworld.user.UserDtos;
import summer.realworld.user.User;
import summer.realworld.user.FollowRepository;
import summer.realworld.user.UserService;
import summer.realworld.auth.AuthUtils;
import summer.realworld.common.Errors;
import summer.realworld.auth.JwtUtil;
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

	private UserDtos.ProfileResponse createProfileResponse(User user, Long currentUserId) {
		boolean following = currentUserId != null && followRepository.isFollowing(currentUserId, user.getId());
		return new UserDtos.ProfileResponse(
				new UserDtos.ProfileResponse.Profile(user.getUsername(), user.getBio(), user.getImage(), following));
	}
}
