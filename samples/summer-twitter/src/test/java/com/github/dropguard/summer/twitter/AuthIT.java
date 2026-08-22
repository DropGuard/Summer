package com.github.dropguard.summer.twitter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Integration coverage for the auth / user-profile surface: registration, login, JWT rejection
 * semantics, and the profile read/update contract (no sensitive fields leaked). The HTTP layer here
 * is what the pure unit tests in {@code User*Test} cannot exercise — token issuance and the 401/404
 * contract.
 */
class AuthIT extends AbstractTwitterIT {

    @Test
    void registerAndLoginFlow() throws Exception {
        String user = "auth_flow_" + System.nanoTime();
        String token = registerAndGetToken(user, "password123").token();
        assertNotNull(token, "JWT token must not be null");
        assertFalse(token.isBlank(), "JWT token must not be blank");
    }

    @Test
    void wrongPasswordReturns401() throws Exception {
        String user = "auth_wrong_" + System.nanoTime();
        registerAndGetToken(user, "password123");

        var res =
                post(
                        "/api/auth/login",
                        """
                        {"username":"%s","password":"wrongpassword"}
                        """
                                .formatted(user));
        assertEquals(401, res.statusCode(), "Wrong password should return 401");
    }

    @Test
    void invalidTokenReturns401() throws Exception {
        var res = authGet("/api/users/me", "not.a.real.jwt");
        assertEquals(401, res.statusCode(), "Invalid token should return 401");
    }

    @Test
    void authenticatedProfileUpdateHidesSensitiveFields() throws Exception {
        String token = registerAndGetToken("auth_prof_" + System.nanoTime(), "password123").token();

        var updateRes =
                put(
                        "/api/users/me",
                        token,
                        """
                        {"displayName":"Updated Name","bio":"Integration test bio"}
                        """);
        assertEquals(200, updateRes.statusCode(), "Profile update should succeed");

        Map<?, ?> profile = mapper.readValue(updateRes.body(), Map.class);
        assertEquals("Updated Name", profile.get("displayName"));
        assertEquals("Integration test bio", profile.get("bio"));
        assertFalse(profile.containsKey("email"), "Must not expose email");
        assertFalse(profile.containsKey("passwordHash"), "Must not expose password hash");
        assertNotNull(profile.get("createdAt"), "Must include created_at timestamp");
    }

    @Test
    void readNonExistentUserReturns404() throws Exception {
        String token =
                registerAndGetToken("auth_reader_" + System.nanoTime(), "password123").token();

        var res = authGet("/api/users/profile_tester", token);
        assertEquals(404, res.statusCode(), "Non-existent user should return 404");
    }

    @Test
    void registrationRequiresUniqueUsername() throws Exception {
        String user = "auth_dup_" + System.nanoTime();
        registerAndGetToken(user, "password123");

        var dup =
                post(
                        "/api/auth/register",
                        """
                        {"username":"%s","displayName":"Dup","email":"dup@test.com","password":"password123"}
                        """
                                .formatted(user));
        assertEquals(400, dup.statusCode(), "Duplicate username should be rejected");
        // Error body is plain text ("Username already exists"), not JSON — no token
        // is issued on a rejected registration.
        assertTrue(!dup.body().isBlank(), "Rejection must carry an error message");
    }
}
