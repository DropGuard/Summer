package summer.twitter.auth;

import org.mindrot.jbcrypt.BCrypt;
import summer.core.Component;
import summer.twitter.user.User;
import summer.twitter.user.UserRepository;
import summer.web.HttpContext;
import summer.web.HttpStatus;
import summer.web.annotation.Post;
import summer.web.annotation.RestController;

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

    public record RegisterRequest(String username, String displayName, String email, String password) {}
    public record LoginRequest(String username, String password) {}
    public record TokenResponse(String token) {}

    @Post("/api/auth/register")
    public void register(HttpContext ctx) {
        RegisterRequest req = ctx.body(RegisterRequest.class);

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
        LoginRequest req = ctx.body(LoginRequest.class);

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