package com.github.dropguard.summer.web.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.github.dropguard.summer.web.Handler;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpMethod;
import com.github.dropguard.summer.web.HttpRouter;
import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.Request;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ByteRouterTest {

    @Test
    void testStaticRouteMatching() throws Exception {
        Handler handler = ctx -> ctx.text(HttpStatus.OK, "ok");
        RadixTreeHttpRouter router =
                new RadixTreeHttpRouter(
                        List.of(
                                new HttpRouter.Builder.Route(
                                        HttpMethod.GET, "/api/users", handler)));

        HttpContext ctx = createMockContext(HttpMethod.GET, "/api/users");
        router.route(ctx);

        assertNotNull(ctx.body());
        assertEquals("ok", new String(ctx.body(), StandardCharsets.UTF_8));
    }

    @Test
    void testPathParameterMatching() throws Exception {
        Handler handler = ctx -> ctx.text(HttpStatus.OK, "user-" + ctx.request().pathParam("id"));
        RadixTreeHttpRouter router =
                new RadixTreeHttpRouter(
                        List.of(
                                new HttpRouter.Builder.Route(
                                        HttpMethod.GET, "/users/{id}", handler)));

        HttpContext ctx = createMockContext(HttpMethod.GET, "/users/42");
        router.route(ctx);

        assertEquals("user-42", new String(ctx.body(), StandardCharsets.UTF_8));
    }

    @Test
    void testDeepNestedRoutes() throws Exception {
        RadixTreeHttpRouter router =
                new RadixTreeHttpRouter(
                        List.of(
                                new HttpRouter.Builder.Route(
                                        HttpMethod.GET,
                                        "/api/v1/products/search",
                                        ctx -> ctx.text(HttpStatus.OK, "search")),
                                new HttpRouter.Builder.Route(
                                        HttpMethod.POST,
                                        "/api/v1/products",
                                        ctx -> ctx.text(HttpStatus.OK, "create"))));

        assertEquals("search", bodyOf(router, HttpMethod.GET, "/api/v1/products/search"));
        assertEquals("create", bodyOf(router, HttpMethod.POST, "/api/v1/products"));

        HttpContext noMatch = createMockContext(HttpMethod.GET, "/api/v1/products");
        router.route(noMatch);
        assertNull(noMatch.body());
    }

    @Test
    void testRootPath() throws Exception {
        RadixTreeHttpRouter router =
                new RadixTreeHttpRouter(
                        List.of(
                                new HttpRouter.Builder.Route(
                                        HttpMethod.GET,
                                        "/",
                                        ctx -> ctx.text(HttpStatus.OK, "home"))));
        assertEquals("home", bodyOf(router, HttpMethod.GET, "/"));
        assertEquals("home", bodyOf(router, HttpMethod.GET, ""));
    }

    @Test
    void testTrailingSlash() throws Exception {
        RadixTreeHttpRouter router =
                new RadixTreeHttpRouter(
                        List.of(
                                new HttpRouter.Builder.Route(
                                        HttpMethod.GET,
                                        "/users",
                                        ctx -> ctx.text(HttpStatus.OK, "list"))));
        assertEquals("list", bodyOf(router, HttpMethod.GET, "/users/"));
    }

    private String bodyOf(RadixTreeHttpRouter router, HttpMethod method, String path)
            throws Exception {
        HttpContext ctx = createMockContext(method, path);
        router.route(ctx);
        byte[] body = ctx.body();
        return body != null ? new String(body, StandardCharsets.UTF_8) : null;
    }

    private HttpContext createMockContext(HttpMethod method, String path) {
        Request request =
                new Request(method, path, "", "application/json", new byte[0], new HashMap<>());
        return new HttpContext(request);
    }
}
