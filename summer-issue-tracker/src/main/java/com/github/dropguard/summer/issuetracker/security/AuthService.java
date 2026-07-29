package com.github.dropguard.summer.issuetracker.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.issuetracker.common.BusinessException;
import com.github.dropguard.summer.issuetracker.org.Organization;
import com.github.dropguard.summer.issuetracker.org.OrganizationRepository;
import com.github.dropguard.summer.issuetracker.user.User;
import com.github.dropguard.summer.issuetracker.user.UserService;

/**
 * Bootstrap auth: register a user (auto-provisioning its organization on first
 * signup) and login issuing a JWT. Passwords are hashed with SHA-256 + per-user
 * random salt, stored as {@code salt:hash} in the password column.
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
        // Try-insert the organization; if another concurrent registration already
        // created the same slug, the DB UNIQUE constraint prevents duplicates and
        // the insertOrIgnore falls through cleanly.
        organizationRepository.insertOrIgnore(new Organization(orgId, orgName, orgSlug,
                java.time.OffsetDateTime.now()));
        User user = userService.registerUser(orgId, username, displayName, email,
                hashPassword(password), Role.MEMBER);
        return new AuthResult(user.id(), user.username(), jwtUtil.generate(user.id(), user.username()));
    }

    public AuthResult login(String username, String password) {
        User user = userService.findByUsername(username)
                .orElseThrow(() -> BusinessException.unauthorized("Invalid credentials"));
        if (!verifyPassword(user.passwordHash(), password)) {
            throw BusinessException.unauthorized("Invalid credentials");
        }
        return new AuthResult(user.id(), user.username(), jwtUtil.generate(user.id(), user.username()));
    }

    private static long hashOrgId(String slug) {
        // Deterministic org id so re-registration with the same slug lands in the same org.
        return Math.floorMod(slug.hashCode(), 1_000_000_000L) + 1_000_000_000L;
    }

    private static final SecureRandom RNG = new SecureRandom();

    private static String hashPassword(String password) {
        byte[] salt = new byte[16];
        RNG.nextBytes(salt);
        return HexFormat.of().formatHex(salt) + ":"
                + sha256(salt, password);
    }

    private static boolean verifyPassword(String stored, String password) {
        int sep = stored.indexOf(':');
        if (sep < 0) return false;
        byte[] salt = HexFormat.of().parseHex(stored.substring(0, sep));
        return sha256(salt, password).equals(stored.substring(sep + 1));
    }

    private static String sha256(byte[] salt, String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            md.update(password.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            throw new IllegalStateException("Password hashing failed", e);
        }
    }
}
