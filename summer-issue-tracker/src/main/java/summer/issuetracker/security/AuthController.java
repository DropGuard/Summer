package summer.issuetracker.security;

import summer.core.Component;
import summer.web.HttpContext;
import summer.web.HttpStatus;
import summer.web.RequestAttributes;
import summer.web.annotation.Get;
import summer.web.annotation.Post;
import summer.web.annotation.RestController;

@RestController
@Component
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    public record RegisterRequest(String username, String displayName, String email,
            String password, String orgName, String orgSlug) {}

    public record LoginRequest(String username, String password) {}

    @Post("/api/auth/register")
    public void register(HttpContext ctx) {
        RegisterRequest req = ctx.body(RegisterRequest.class);
        AuthService.AuthResult result = authService.register(
                req.username(), req.displayName(), req.email(), req.password(), req.orgName(), req.orgSlug());
        ctx.json(HttpStatus.CREATED, new Token(result.userId(), result.username(), result.token()));
    }

    @Post("/api/auth/login")
    public void login(HttpContext ctx) {
        LoginRequest req = ctx.body(LoginRequest.class);
        AuthService.AuthResult result = authService.login(req.username(), req.password());
        ctx.json(HttpStatus.OK, new Token(result.userId(), result.username(), result.token()));
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

    public record Token(Long userId, String username, String token) {}
    public record MeResponse(Long userId) {}
}
