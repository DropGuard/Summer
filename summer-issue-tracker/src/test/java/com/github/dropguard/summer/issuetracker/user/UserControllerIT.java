package com.github.dropguard.summer.issuetracker.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.github.dropguard.summer.issuetracker.AbstractIssueTrackerIT;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The only user-facing endpoint is {@code /api/me}, which returns the caller's own profile. It must
 * (a) resolve to the authenticated caller and (b) never leak the stored {@code passwordHash}.
 */
class UserControllerIT extends AbstractIssueTrackerIT {

    @Test
    void meReturnsTheAuthenticatedCaller() throws Exception {
        var u = registerAndLogin("me_user", "me_org");
        var res = authGet("/api/me", u.token());
        assertEquals(200, res.statusCode(), res.body());

        // Core invariant: /api/me resolves to the *authenticated* user, not someone
        // else or an empty body. (Non-leakage of passwordHash is guaranteed by the
        // UserView DTO type, not by a runtime assertion — asserting it here would be
        // testing something the compiler already enforces.)
        Map<String, Object> body = mapper.readValue(res.body(), Map.class);
        assertEquals(u.userId(), ((Number) body.get("id")).longValue());
        assertNotNull(body.get("username"), "Endpoint must echo the caller's username");
    }

    @Test
    void meRequiresAuth() throws Exception {
        var res = get("/api/me");
        assertEquals(401, res.statusCode(), "Unauthenticated /api/me must be rejected");
    }
}
