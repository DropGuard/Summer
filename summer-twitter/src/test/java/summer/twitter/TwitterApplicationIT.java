package summer.twitter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;

/**
 * Smoke integration test: proves the full stack (DI, web, real Postgres, real
 * Redis) boots and the most basic paths respond. Detailed behavior of each
 * domain lives in the focused ITs ({@code AuthIT}, {@code TweetIT},
 * {@code TimelineIT}, {@code MessageWSIT}); this class only guards against
 * "the application does not come up at all".
 */
class TwitterApplicationIT extends AbstractTwitterIT {

    @Test
    void healthEndpointsArePublic() throws Exception {
        assertEquals(200, get("/health/live").statusCode());
        assertEquals(200, get("/health/ready").statusCode());
    }

    @Test
    void protectedRouteRequiresAuth() throws Exception {
        assertEquals(401, get("/api/users/me").statusCode());

        HttpResponse<String> res = client.send(
                java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(baseUrl + "/api/users/me"))
                        .header("Authorization", "Bearer invalid.token.here")
                        .GET()
                        .build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(401, res.statusCode(), "Invalid token should return 401");
    }

    @Test
    void registerAndLoginHappyPath() throws Exception {
        String user = "smoke_" + System.nanoTime();
        String token = registerAndGetToken(user, "pass123").token();
        assertNotNull(token, "JWT token must not be null");
        assertFalse(token.isBlank(), "JWT token must not be blank");
    }
}
