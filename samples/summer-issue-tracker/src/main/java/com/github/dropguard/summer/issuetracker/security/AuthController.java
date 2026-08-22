package com.github.dropguard.summer.issuetracker.security;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.RequestAttributes;
import com.github.dropguard.summer.web.annotation.Get;
import com.github.dropguard.summer.web.annotation.Post;
import com.github.dropguard.summer.web.annotation.RestController;

@RestController
@Component
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;

    public AuthController(AuthService authService, JwtUtil jwtUtil) {
        this.authService = authService;
        this.jwtUtil = jwtUtil;
    }

    public record RegisterRequest(
            String username,
            String displayName,
            @jakarta.validation.constraints.Email String email,
            @jakarta.validation.constraints.Size(min = 8) String password,
            String orgName,
            String orgSlug) {}

    public record LoginRequest(String username, String password) {}

    public record RefreshRequest(@jakarta.validation.constraints.NotBlank String refreshToken) {}

    @Post("/api/auth/register")
    public void register(HttpContext ctx) {
        RegisterRequest req = ctx.body(RegisterRequest.class);
        AuthService.AuthResult result =
                authService.register(
                        req.username(),
                        req.displayName(),
                        req.email(),
                        req.password(),
                        req.orgName(),
                        req.orgSlug());
        ctx.json(
                HttpStatus.CREATED,
                new Token(
                        result.userId(), result.username(), result.token(), result.refreshToken()));
    }

    @Post("/api/auth/login")
    public void login(HttpContext ctx) {
        LoginRequest req = ctx.body(LoginRequest.class);
        AuthService.AuthResult result = authService.login(req.username(), req.password());
        ctx.json(
                HttpStatus.OK,
                new Token(
                        result.userId(), result.username(), result.token(), result.refreshToken()));
    }

    @Post("/api/auth/refresh")
    public void refreshToken(HttpContext ctx) {
        RefreshRequest req = ctx.body(RefreshRequest.class);
        Long userId = jwtUtil.validateRefreshToken(req.refreshToken());
        if (userId == null) {
            ctx.status(HttpStatus.UNAUTHORIZED);
            return;
        }
        String newAccess = jwtUtil.generateAccessToken(userId, "");
        String newRefresh = jwtUtil.generateRefreshToken(userId);
        ctx.ok(new Token(userId, "", newAccess, newRefresh));
    }

    @Get("/api/auth/me")
    public void me(HttpContext ctx) {
        Long userId = ctx.request().getAttribute(RequestAttributes.USER_ID);
        if (userId == null) {
            ctx.status(HttpStatus.UNAUTHORIZED);
            return;
        }
        ctx.ok(new MeResponse(userId));
    }

    public record Token(Long userId, String username, String token, String refreshToken) {}

    public record MeResponse(Long userId) {}
}
