package com.github.dropguard.summer.twitter.auth;

import com.github.dropguard.summer.twitter.user.User;
import com.github.dropguard.summer.twitter.common.UsernameAlreadyExistsException;
import com.github.dropguard.summer.twitter.common.EmailAlreadyExistsException;
import com.github.dropguard.summer.twitter.common.RateLimitedException;
import com.github.dropguard.summer.twitter.common.InvalidCredentialsException;
import com.github.dropguard.summer.twitter.common.InvalidTokenException;
import com.github.dropguard.summer.twitter.user.UserRepository;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.annotation.Post;
import com.github.dropguard.summer.web.annotation.RestController;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.mindrot.jbcrypt.BCrypt;

@RestController
public class AuthController {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final LoginRateLimiter rateLimiter;

    public AuthController(
            UserRepository userRepository, JwtUtil jwtUtil, LoginRateLimiter rateLimiter) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.rateLimiter = rateLimiter;
    }

    @Valid
    public record RegisterRequest(
            @jakarta.validation.constraints.NotBlank String username,
            @jakarta.validation.constraints.NotBlank String displayName,
            @jakarta.validation.constraints.Email String email,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(min = 8)
                    String password) {}

    @Valid
    public record LoginRequest(
            @jakarta.validation.constraints.NotBlank String username,
            @jakarta.validation.constraints.NotBlank String password) {}

    @Valid
    public record RefreshRequest(@jakarta.validation.constraints.NotBlank String refreshToken) {}

    public record TokenResponse(String token, String refreshToken) {}

    @Post("/api/auth/register")
    public void register(HttpContext ctx) {
        RegisterRequest req = ctx.validatedBody(RegisterRequest.class);

        Optional<User> existingUser = userRepository.findByUsername(req.username());
        if (existingUser.isPresent()) {
            throw new UsernameAlreadyExistsException("Username already exists");
        }

        Optional<User> existingEmail = userRepository.findByEmail(req.email());
        if (existingEmail.isPresent()) {
            throw new EmailAlreadyExistsException("Email already exists");
        }

        String passwordHash = BCrypt.hashpw(req.password(), BCrypt.gensalt());

        User user =
                new User(
                        null,
                        req.username(),
                        req.displayName(),
                        req.email(),
                        passwordHash,
                        "",
                        0,
                        0,
                        OffsetDateTime.now());
        userRepository.insert(user);

        String accessToken = jwtUtil.generateAccessToken(user.id(), user.username());
        String refreshToken = jwtUtil.generateRefreshToken(user.id());
        ctx.json(HttpStatus.CREATED, new TokenResponse(accessToken, refreshToken));
    }

    @Post("/api/auth/login")
    public void login(HttpContext ctx) {
        LoginRequest req = ctx.validatedBody(LoginRequest.class);

        if (rateLimiter.isBlocked(req.username())) {
            throw new RateLimitedException("Too many login attempts, try again later");
        }

        Optional<User> userOpt = userRepository.findByUsername(req.username());
        if (userOpt.isEmpty()) {
            rateLimiter.recordFailure(req.username());
            throw new InvalidCredentialsException("Invalid username or password");
        }

        User user = userOpt.get();
        if (!BCrypt.checkpw(req.password(), user.passwordHash())) {
            rateLimiter.recordFailure(req.username());
            throw new InvalidCredentialsException("Invalid username or password");
        }

        rateLimiter.reset(req.username());
        String accessToken = jwtUtil.generateAccessToken(user.id(), user.username());
        String refreshToken = jwtUtil.generateRefreshToken(user.id());
        ctx.ok(new TokenResponse(accessToken, refreshToken));
    }

    @Post("/api/auth/refresh")
    public void refreshToken(HttpContext ctx) {
        RefreshRequest req = ctx.validatedBody(RefreshRequest.class);
        Long userId = jwtUtil.validateRefreshToken(req.refreshToken());

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            throw new InvalidTokenException("Invalid token");
        }

        User user = userOpt.get();
        String newAccess = jwtUtil.generateAccessToken(user.id(), user.username());
        String newRefresh = jwtUtil.generateRefreshToken(user.id());
        ctx.ok(new TokenResponse(newAccess, newRefresh));
    }
}
