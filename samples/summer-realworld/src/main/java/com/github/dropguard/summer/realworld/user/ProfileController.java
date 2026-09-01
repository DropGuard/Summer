package com.github.dropguard.summer.realworld.user;

import com.github.dropguard.summer.realworld.auth.AuthUtils;
import com.github.dropguard.summer.realworld.auth.JwtUtil;
import com.github.dropguard.summer.realworld.common.ProfileNotFoundException;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.annotation.Delete;
import com.github.dropguard.summer.web.annotation.Get;
import com.github.dropguard.summer.web.annotation.PathParam;
import com.github.dropguard.summer.web.annotation.Post;
import com.github.dropguard.summer.web.annotation.RestController;
import java.util.Optional;

@RestController("/api")
public class ProfileController {
    private final UserService userService;
    private final FollowService followService;
    private final JwtUtil jwtUtil;

    public ProfileController(
            UserService userService, FollowService followService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.followService = followService;
        this.jwtUtil = jwtUtil;
    }

    @Get("/profiles/{username}")
    public void getProfile(HttpContext ctx, @PathParam("username") String username) {
        Optional<User> userOpt = userService.findByUsername(username);
        if (userOpt.isEmpty()) {
            throw new ProfileNotFoundException("Profile not found");
        }

        Long currentUserId = AuthUtils.tryGetCurrentUserId(ctx, jwtUtil);
        ctx.json(HttpStatus.OK, createProfileResponse(userOpt.get(), currentUserId));
    }

    @Post("/profiles/{username}/follow")
    public void followUser(HttpContext ctx, @PathParam("username") String username) {
        Long currentUserId = AuthUtils.getCurrentUserId(ctx, jwtUtil);

        Optional<User> userOpt = userService.findByUsername(username);
        if (userOpt.isEmpty()) {
            throw new ProfileNotFoundException("Profile not found");
        }

        followService.follow(currentUserId, userOpt.get().id());
        ctx.json(HttpStatus.OK, createProfileResponse(userOpt.get(), currentUserId));
    }

    @Delete("/profiles/{username}/follow")
    public void unfollowUser(HttpContext ctx, @PathParam("username") String username) {
        Long currentUserId = AuthUtils.getCurrentUserId(ctx, jwtUtil);

        Optional<User> userOpt = userService.findByUsername(username);
        if (userOpt.isEmpty()) {
            throw new ProfileNotFoundException("Profile not found");
        }

        followService.unfollow(currentUserId, userOpt.get().id());
        ctx.json(HttpStatus.OK, createProfileResponse(userOpt.get(), currentUserId));
    }

    private UserDtos.ProfileResponse createProfileResponse(User user, Long currentUserId) {
        boolean following = followService.isFollowing(currentUserId, user.id());
        return new UserDtos.ProfileResponse(
                new UserDtos.ProfileResponse.Profile(
                        user.username(), user.bio(), user.image(), following));
    }
}
