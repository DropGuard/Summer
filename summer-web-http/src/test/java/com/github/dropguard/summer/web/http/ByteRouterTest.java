package com.github.dropguard.summer.web.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.github.dropguard.summer.web.Handler;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpMethod;
import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.Request;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ByteRouterTest {

    private RadixTreeHttpRouter router;

    @BeforeEach
    void setUp() {
        router = new RadixTreeHttpRouter();
    }

    @Test
    void testStaticRouteMatching() {
        Handler handler = ctx -> ctx.text(HttpStatus.OK, "ok");
        router.register(HttpMethod.GET, "/api/users", handler);

        HttpContext ctx = createMockContext(HttpMethod.GET, "/api/users");
        router.route(ctx);

        assertNotNull(ctx.body());
        assertEquals("ok", new String(ctx.body(), StandardCharsets.UTF_8));
    }

    @Test
    void testPathParameterMatching() {
        Handler handler = ctx -> ctx.text(HttpStatus.OK, "user-" + ctx.request().pathParam("id"));
        router.register(HttpMethod.GET, "/users/{id}", handler);

        HttpContext ctx = createMockContext(HttpMethod.GET, "/users/42");
        router.route(ctx);

        assertEquals("user-42", new String(ctx.body(), StandardCharsets.UTF_8));
    }

    @Test
    void testDeepNestedRoutes() {
        router.register(
                HttpMethod.GET,
                "/api/v1/products/search",
                ctx -> ctx.text(HttpStatus.OK, "search"));
        router.register(
                HttpMethod.POST, "/api/v1/products", ctx -> ctx.text(HttpStatus.OK, "create"));

        assertEquals("search", bodyOf(HttpMethod.GET, "/api/v1/products/search"));
        assertEquals("create", bodyOf(HttpMethod.POST, "/api/v1/products"));

        HttpContext noMatch = createMockContext(HttpMethod.GET, "/api/v1/products");
        router.route(noMatch);
        assertNull(noMatch.body());
    }

    @Test
    void testRootPath() {
        router.register(HttpMethod.GET, "/", ctx -> ctx.text(HttpStatus.OK, "home"));
        assertEquals("home", bodyOf(HttpMethod.GET, "/"));
        assertEquals("home", bodyOf(HttpMethod.GET, ""));
    }

    @Test
    void testTrailingSlash() {
        router.register(HttpMethod.GET, "/users", ctx -> ctx.text(HttpStatus.OK, "list"));
        assertEquals("list", bodyOf(HttpMethod.GET, "/users/"));
    }

    private String bodyOf(HttpMethod method, String path) {
        HttpContext ctx = createMockContext(method, path);
        router.route(ctx);
        byte[] body = ctx.body();
        return body != null ? new String(body, StandardCharsets.UTF_8) : null;
    }

    private HttpContext createMockContext(HttpMethod method, String path) {
        byte[] pathBytes = path.getBytes(StandardCharsets.UTF_8);
        Request request =
                new Request(
                        method,
                        path,
                        "",
                        "application/json",
                        new byte[0],
                        new HashMap<>(),
                        pathBytes);
        return new HttpContext(request);
    }
}
