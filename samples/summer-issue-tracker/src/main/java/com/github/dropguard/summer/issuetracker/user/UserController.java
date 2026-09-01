package com.github.dropguard.summer.issuetracker.user;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.issuetracker.security.Actors;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.annotation.Get;
import com.github.dropguard.summer.web.annotation.RestController;
import com.github.dropguard.summer.web.exception.NotFoundException;

/**
 * Read-only current-user endpoint. Returns a {@link UserView} that never exposes the stored {@code
 * passwordHash} — the {@link User} entity is internal only.
 */
@RestController
@Component
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public record UserView(
            Long id, Long orgId, String username, String displayName, String email, String role) {
        static UserView from(User u) {
            return new UserView(
                    u.id(), u.orgId(), u.username(), u.displayName(), u.email(), u.role());
        }
    }

    @Get("/api/me")
    public void me(HttpContext ctx) {
        long userId = Actors.require(ctx);
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new NotFoundException("User not found"));
        ctx.ok(UserView.from(user));
    }
}
