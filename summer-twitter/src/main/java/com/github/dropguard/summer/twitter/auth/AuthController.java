package com.github.dropguard.summer.twitter.auth;

import org.mindrot.jbcrypt.BCrypt;
import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.twitter.user.User;
import com.github.dropguard.summer.twitter.user.UserRepository;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.annotation.Post;
import com.github.dropguard.summer.web.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Optional;

@RestController
public class AuthController {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    public AuthController(UserRepository userRepository, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
    }

    public record RegisterRequest(
            @jakarta.validation.constraints.NotBlank String username,
            @jakarta.validation.constraints.NotBlank String displayName,
            @jakarta.validation.constraints.Email String email,
            @jakarta.validation.constraints.NotBlank String password) {}
    public record LoginRequest(
            @jakarta.validation.constraints.NotBlank String username,
            @jakarta.validation.constraints.NotBlank String password) {}
    public record TokenResponse(String token) {}

    @Post("/api/auth/register")
    public void register(HttpContext ctx) {
        RegisterRequest req = ctx.validatedBody(RegisterRequest.class);

        Optional<User> existingUser = userRepository.findByUsername(req.username());
        if (existingUser.isPresent()) {
            ctx.text(HttpStatus.BAD_REQUEST, "Username already exists");
            return;
        }

        String passwordHash = BCrypt.hashpw(req.password(), BCrypt.gensalt());

        User user = new User(
            null,
            req.username(),
            req.displayName(),
            req.email(),
            passwordHash,
            "",
            0,
            0,
            OffsetDateTime.now()
        );
        userRepository.insert(user);

        ctx.status(HttpStatus.CREATED);
    }

    @Post("/api/auth/login")
    public void login(HttpContext ctx) {
        LoginRequest req = ctx.validatedBody(LoginRequest.class);

        Optional<User> userOpt = userRepository.findByUsername(req.username());
        if (userOpt.isEmpty()) {
            ctx.text(HttpStatus.UNAUTHORIZED, "Invalid username or password");
            return;
        }

        User user = userOpt.get();
        if (!BCrypt.checkpw(req.password(), user.passwordHash())) {
            ctx.text(HttpStatus.UNAUTHORIZED, "Invalid username or password");
            return;
        }

        String token = jwtUtil.generate(user.id(), user.username());
        ctx.ok(new TokenResponse(token));
    }
}