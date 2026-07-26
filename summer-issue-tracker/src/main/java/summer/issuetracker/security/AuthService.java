package summer.issuetracker.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

import summer.core.Component;
import summer.issuetracker.common.BusinessException;
import summer.issuetracker.org.Organization;
import summer.issuetracker.org.OrganizationRepository;
import summer.issuetracker.user.User;
import summer.issuetracker.user.UserService;

/**
 * Bootstrap auth: register a user (auto-provisioning its organization on first
 * signup) and login issuing a JWT. Passwords are hashed with SHA-256 — adequate
 * for a demo, not for production (use BCrypt/Argon2 there).
 */
@Component
public class AuthService {

    private final UserService userService;
    private final OrganizationRepository organizationRepository;
    private final JwtUtil jwtUtil;

    public AuthService(UserService userService, OrganizationRepository organizationRepository, JwtUtil jwtUtil) {
        this.userService = userService;
        this.organizationRepository = organizationRepository;
        this.jwtUtil = jwtUtil;
    }

    public record AuthResult(Long userId, String username, String token) {}

    public AuthResult register(String username, String displayName, String email, String password, String orgName, String orgSlug) {
        if (userService.findByUsername(username).isPresent()) {
            throw BusinessException.badRequest("Username already taken");
        }
        // Demo simplification: every registration creates its own organization. The
        // new user is a plain MEMBER — ADMIN/MANAGER are granted explicitly (e.g. a
        // project's creator becomes its MANAGER+lead via ProjectController.create),
        // so the demo's project-level RBAC actually means something. Giving every
        // signup ADMIN would let hasAtLeast(ADMIN) short-circuit all project
        // membership checks and make RBAC cosmetic.
        long orgId = hashOrgId(orgSlug);
        // Ensure the organization row exists before registering the user.
        if (organizationRepository.findBySlug(orgSlug).isEmpty()) {
            organizationRepository.insert(new Organization(orgId, orgName, orgSlug, java.time.OffsetDateTime.now()));
        }
        User user = userService.registerUser(orgId, username, displayName, email,
                hashPassword(password), Role.MEMBER);
        return new AuthResult(user.id(), user.username(), jwtUtil.generate(user.id(), user.username()));
    }

    public AuthResult login(String username, String password) {
        User user = userService.findByUsername(username)
                .orElseThrow(() -> BusinessException.unauthorized("Invalid credentials"));
        if (!user.passwordHash().equals(hashPassword(password))) {
            throw BusinessException.unauthorized("Invalid credentials");
        }
        return new AuthResult(user.id(), user.username(), jwtUtil.generate(user.id(), user.username()));
    }

    private static long hashOrgId(String slug) {
        // Deterministic org id so re-registration with the same slug lands in the same org.
        return Math.floorMod(slug.hashCode(), 1_000_000_000L) + 1_000_000_000L;
    }

    private static String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Password hashing failed", e);
        }
    }
}
