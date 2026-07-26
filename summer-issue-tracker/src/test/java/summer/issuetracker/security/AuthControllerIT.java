package summer.issuetracker.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import summer.issuetracker.AbstractIssueTrackerIT;

/**
 * Auth endpoints: registration, login, and the /api/auth/me introspection.
 * Covers the credential/token boundaries — wrong password, malformed token, and
 * missing auth are all rejected with 401; a duplicate username is 400.
 *
 * <p>Note on expired tokens: the app signs with a secret held only in
 * application config, so the test cannot mint a valid-but-expired token. A
 * tampered/malformed token exercises the same security path — JwtAuthMiddleware
 * calls JwtUtil.extractClaims, which throws on any signature/expiry failure, the
 * middleware returns null, and downstream code rejects the request as anonymous
 * (401). So the malformed-token case is the observable guard for "bad token".</p>
 */
class AuthControllerIT extends AbstractIssueTrackerIT {

    @Test
    void registerReturnsToken() throws Exception {
        var res = post("/api/auth/register", """
                {"username":"auth_reg","displayName":"Reg","email":"reg@t.com","password":"pw","orgName":"Auth Org","orgSlug":"auth_org"}
                """, null);
        assertEquals(201, res.statusCode(), res.body());
        Map<String, Object> body = mapper.readValue(res.body(), Map.class);
        assertNotNull(body.get("token"), "Registered user receives a JWT");
        assertNotNull(body.get("userId"));
    }

    @Test
    void loginSucceedsWithCorrectPassword() throws Exception {
        post("/api/auth/register", """
                {"username":"auth_login","displayName":"L","email":"l@t.com","password":"pw","orgName":"Auth Org2","orgSlug":"auth_org2"}
                """, null);

        var res = post("/api/auth/login", """
                {"username":"auth_login","password":"pw"}
                """, null);
        assertEquals(200, res.statusCode(), res.body());
        assertNotNull(mapper.readValue(res.body(), Map.class).get("token"));
    }

    @Test
    void loginFailsWithWrongPassword() throws Exception {
        post("/api/auth/register", """
                {"username":"auth_bad","displayName":"B","email":"b@t.com","password":"pw","orgName":"Auth Org3","orgSlug":"auth_org3"}
                """, null);

        var res = post("/api/auth/login", """
                {"username":"auth_bad","password":"wrong"}
                """, null);
        assertEquals(401, res.statusCode(), "Wrong password must be rejected");
    }

    @Test
    void meRequiresValidToken() throws Exception {
        var u = registerAndLogin("auth_me", "auth_me_org");

        var ok = authGet("/api/auth/me", u.token());
        assertEquals(200, ok.statusCode(), ok.body());
        Map<String, Object> body = mapper.readValue(ok.body(), Map.class);
        assertEquals(u.userId(), ((Number) body.get("userId")).longValue());

        var anon = get("/api/auth/me");
        assertEquals(401, anon.statusCode(), "Missing auth must be rejected");

        var bad = authGet("/api/auth/me", "not-a-real-token");
        assertEquals(401, bad.statusCode(), "Malformed token must be rejected");
    }

    @Test
    void duplicateUsernameIsRejected() throws Exception {
        post("/api/auth/register", """
                {"username":"auth_dup","displayName":"D","email":"d@t.com","password":"pw","orgName":"Auth Org4","orgSlug":"auth_org4"}
                """, null);

        var res = post("/api/auth/register", """
                {"username":"auth_dup","displayName":"D2","email":"d2@t.com","password":"pw","orgName":"Auth Org5","orgSlug":"auth_org5"}
                """, null);
        assertEquals(400, res.statusCode(), "Duplicate username must be 400");
    }
}
