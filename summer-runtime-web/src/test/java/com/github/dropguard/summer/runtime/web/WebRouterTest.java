package com.github.dropguard.summer.runtime.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpMethod;
import com.github.dropguard.summer.web.HttpRouter;
import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.Request;
import com.github.dropguard.summer.web.exception.RouteConflictException;
import com.github.dropguard.summer.web.http.MapRouter;
import com.github.dropguard.summer.web.http.RadixTreeHttpRouter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class WebRouterTest {

    @Test
    void basicGetRoute() throws Exception {
        RadixTreeHttpRouter router =
                new RadixTreeHttpRouter(
                        List.of(
                                new HttpRouter.Builder.Route(
                                        HttpMethod.GET,
                                        "/hello",
                                        ctx -> ctx.text(HttpStatus.OK, "world"))));
        Request req = new Request(HttpMethod.GET, "/hello", null, null, null);
        HttpContext ctx = new HttpContext(req);
        router.route(ctx);
        assertEquals("world", new String(ctx.body(), StandardCharsets.UTF_8));
    }

    @Test
    void basicPostRoute() throws Exception {
        RadixTreeHttpRouter router =
                new RadixTreeHttpRouter(
                        List.of(
                                new HttpRouter.Builder.Route(
                                        HttpMethod.POST,
                                        "/items",
                                        ctx -> ctx.text(HttpStatus.OK, "created"))));
        Request req =
                new Request(
                        HttpMethod.POST,
                        "/items",
                        null,
                        "application/json",
                        "{\"name\":\"test\"}".getBytes(StandardCharsets.UTF_8));
        HttpContext ctx = new HttpContext(req);
        router.route(ctx);
        assertEquals("created", new String(ctx.body(), StandardCharsets.UTF_8));
    }

    @Test
    void pathParameters() throws Exception {
        RadixTreeHttpRouter router =
                new RadixTreeHttpRouter(
                        List.of(
                                new HttpRouter.Builder.Route(
                                        HttpMethod.GET,
                                        "/users/{id}",
                                        ctx -> {
                                            String id = ctx.request().pathParam("id");
                                            ctx.text(HttpStatus.OK, "user:" + id);
                                        })));
        Request req = new Request(HttpMethod.GET, "/users/42", null, null, null);
        HttpContext ctx = new HttpContext(req);
        router.route(ctx);
        assertEquals("user:42", new String(ctx.body(), StandardCharsets.UTF_8));
    }

    @Test
    void unmatchedRouteReturnsNull() throws Exception {
        RadixTreeHttpRouter router =
                new RadixTreeHttpRouter(
                        List.of(
                                new HttpRouter.Builder.Route(
                                        HttpMethod.GET,
                                        "/exists",
                                        ctx -> ctx.text(HttpStatus.OK, "yes"))));
        Request req = new Request(HttpMethod.GET, "/not-exists", null, null, null);
        HttpContext ctx = new HttpContext(req);
        router.route(ctx);
        assertNull(ctx.body());
    }

    @Test
    void methodDistinction() throws Exception {
        RadixTreeHttpRouter router =
                new RadixTreeHttpRouter(
                        List.of(
                                new HttpRouter.Builder.Route(
                                        HttpMethod.GET,
                                        "/resource",
                                        ctx -> ctx.text(HttpStatus.OK, "get")),
                                new HttpRouter.Builder.Route(
                                        HttpMethod.POST,
                                        "/resource",
                                        ctx -> ctx.text(HttpStatus.OK, "post")),
                                new HttpRouter.Builder.Route(
                                        HttpMethod.PUT,
                                        "/resource",
                                        ctx -> ctx.text(HttpStatus.OK, "put")),
                                new HttpRouter.Builder.Route(
                                        HttpMethod.DELETE,
                                        "/resource",
                                        ctx -> ctx.text(HttpStatus.OK, "delete"))));

        assertEquals("get", bodyOf(router, HttpMethod.GET, "/resource"));
        assertEquals("post", bodyOf(router, HttpMethod.POST, "/resource"));
        assertEquals("put", bodyOf(router, HttpMethod.PUT, "/resource"));
        assertEquals("delete", bodyOf(router, HttpMethod.DELETE, "/resource"));
    }

    @Test
    void rootPath() throws Exception {
        RadixTreeHttpRouter router =
                new RadixTreeHttpRouter(
                        List.of(
                                new HttpRouter.Builder.Route(
                                        HttpMethod.GET,
                                        "/",
                                        ctx -> ctx.text(HttpStatus.OK, "root"))));
        Request req = new Request(HttpMethod.GET, "/", null, null, null);
        HttpContext ctx = new HttpContext(req);
        router.route(ctx);
        assertEquals("root", new String(ctx.body(), StandardCharsets.UTF_8));
    }

    @Test
    void routeConflictDetection() throws Exception {
        // A conflicting second path param on the same route fails during construction — the router
        // is immutable after build, so the conflict surfaces at the single registration point.
        assertThrows(
                RouteConflictException.class,
                () ->
                        new RadixTreeHttpRouter(
                                List.of(
                                        new HttpRouter.Builder.Route(
                                                HttpMethod.GET,
                                                "/users/{id}",
                                                ctx -> ctx.text(HttpStatus.OK, "user")),
                                        new HttpRouter.Builder.Route(
                                                HttpMethod.GET,
                                                "/users/{name}",
                                                ctx -> ctx.text(HttpStatus.OK, "conflict")))));
    }

    @Test
    void multiplePathParams() throws Exception {
        RadixTreeHttpRouter router =
                new RadixTreeHttpRouter(
                        List.of(
                                new HttpRouter.Builder.Route(
                                        HttpMethod.GET,
                                        "/orgs/{orgId}/repos/{repoId}",
                                        ctx -> {
                                            String org = ctx.request().pathParam("orgId");
                                            String repo = ctx.request().pathParam("repoId");
                                            ctx.text(HttpStatus.OK, org + "/" + repo);
                                        })));
        Request req = new Request(HttpMethod.GET, "/orgs/summer/repos/core", null, null, null);
        HttpContext ctx = new HttpContext(req);
        router.route(ctx);
        assertEquals("summer/core", new String(ctx.body(), StandardCharsets.UTF_8));
    }

    @Test
    void mapRouterWorks() throws Exception {
        MapRouter mapRouter =
                new MapRouter(
                        List.of(
                                new HttpRouter.Builder.Route(
                                        HttpMethod.GET,
                                        "/hello",
                                        ctx -> ctx.text(HttpStatus.OK, "world")),
                                new HttpRouter.Builder.Route(
                                        HttpMethod.GET,
                                        "/users/{id}",
                                        ctx ->
                                                ctx.text(
                                                        HttpStatus.OK,
                                                        "user:" + ctx.request().pathParam("id")))));

        Request req1 = new Request(HttpMethod.GET, "/hello", null, null, null);
        HttpContext ctx1 = new HttpContext(req1);
        mapRouter.route(ctx1);
        assertEquals("world", new String(ctx1.body(), StandardCharsets.UTF_8));

        Request req2 = new Request(HttpMethod.GET, "/users/99", null, null, null);
        HttpContext ctx2 = new HttpContext(req2);
        mapRouter.route(ctx2);
        assertEquals("user:99", new String(ctx2.body(), StandardCharsets.UTF_8));
    }

    @Test
    void deeplyNestedPaths() throws Exception {
        RadixTreeHttpRouter router =
                new RadixTreeHttpRouter(
                        List.of(
                                new HttpRouter.Builder.Route(
                                        HttpMethod.GET,
                                        "/a/b/c/d/e/f",
                                        ctx -> ctx.text(HttpStatus.OK, "deep"))));
        Request req = new Request(HttpMethod.GET, "/a/b/c/d/e/f", null, null, null);
        HttpContext ctx = new HttpContext(req);
        router.route(ctx);
        assertEquals("deep", new String(ctx.body(), StandardCharsets.UTF_8));
    }

    @Test
    void urlEncodedPathParams() throws Exception {
        RadixTreeHttpRouter router =
                new RadixTreeHttpRouter(
                        List.of(
                                new HttpRouter.Builder.Route(
                                        HttpMethod.GET,
                                        "/search/{query}",
                                        c ->
                                                c.text(
                                                        HttpStatus.OK,
                                                        c.request().pathParam("query")))));
        Request req = new Request(HttpMethod.GET, "/search/hello%20world", null, null, null);
        HttpContext ctx = new HttpContext(req);
        router.route(ctx);
        assertEquals("hello world", new String(ctx.body(), StandardCharsets.UTF_8));
    }

    private String bodyOf(RadixTreeHttpRouter r, HttpMethod method, String path) throws Exception {
        HttpContext ctx = new HttpContext(new Request(method, path, null, null, null));
        r.route(ctx);
        byte[] body = ctx.body();
        return body != null ? new String(body, StandardCharsets.UTF_8) : null;
    }
}
