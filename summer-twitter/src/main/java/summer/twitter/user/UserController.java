package summer.twitter.user;

import summer.core.Component;
import summer.web.HttpContext;
import summer.web.HttpStatus;
import summer.web.RequestAttributes;
import summer.web.annotation.Get;
import summer.web.annotation.Put;
import summer.web.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Optional;

@RestController
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public record UserResponse(
        Long id,
        String username,
        String displayName,
        String bio,
        Integer followerCount,
        Integer followingCount,
        OffsetDateTime createdAt
    ) {
        public static UserResponse fromUser(User user) {
            return new UserResponse(
                user.id(),
                user.username(),
                user.displayName(),
                user.bio(),
                user.followerCount(),
                user.followingCount(),
                user.createdAt()
            );
        }
    }

    public record UpdateProfileRequest(String displayName, String bio) {}

    @Get("/api/users/:username")
    public void getUserProfile(HttpContext ctx) {
        String username = ctx.pathParam("username");
        Optional<User> userOpt = userRepository.findByUsername(username);

        if (userOpt.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }

        ctx.ok(UserResponse.fromUser(userOpt.get()));
    }

    @Put("/api/users/me")
    public void updateProfile(HttpContext ctx) {
        Long userId = ctx.request().getAttribute(RequestAttributes.USER_ID);
        if (userId == null) {
            ctx.status(HttpStatus.UNAUTHORIZED);
            return;
        }

        UpdateProfileRequest req = ctx.body(UpdateProfileRequest.class);
        userRepository.updateProfile(userId, req.displayName(), req.bio());

        // Return updated user
        Optional<User> updated = userRepository.findById(userId);
        if (updated.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }

        ctx.ok(UserResponse.fromUser(updated.get()));
    }
}