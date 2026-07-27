package com.github.dropguard.summer.web.middleware;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.web.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Tests for {@link CorsMiddleware}. */
class CorsMiddlewareTest {

    @Test
    void shouldSetCorsHeaders() {
        CorsConfig config = new CorsConfig("*", "GET, POST", "Content-Type", 3600);
        CorsMiddleware middleware = new CorsMiddleware(config);

        AtomicBoolean called = new AtomicBoolean(false);
        Handler handler =
                middleware.apply(
                        ctx -> {
                            called.set(true);
                        });

        HttpContext ctx = ctx(HttpMethod.GET, "/api/test");
        handler.handle(ctx);

        assertTrue(called.get());
        assertEquals("*", ctx.headers().get("Access-Control-Allow-Origin"));
        assertEquals("GET, POST", ctx.headers().get("Access-Control-Allow-Methods"));
        assertEquals("Content-Type", ctx.headers().get("Access-Control-Allow-Headers"));
        assertEquals("3600", ctx.headers().get("Access-Control-Max-Age"));
    }

    @Test
    void shouldHandlePreflightOptionsRequest() {
        CorsConfig config = new CorsConfig("*", "GET, POST, PUT, DELETE", "Content-Type", 3600);
        CorsMiddleware middleware = new CorsMiddleware(config);

        AtomicBoolean called = new AtomicBoolean(false);
        Handler handler =
                middleware.apply(
                        ctx -> {
                            called.set(true);
                        });

        HttpContext ctx = ctx(HttpMethod.OPTIONS, "/api/test");
        handler.handle(ctx);

        assertFalse(called.get(), "Next handler should NOT be called for preflight");
        assertEquals(HttpStatus.NO_CONTENT, ctx.statusCode());
        assertEquals("*", ctx.headers().get("Access-Control-Allow-Origin"));
    }

    @Test
    void shouldDelegateNonOptionsRequests() {
        CorsConfig config = new CorsConfig("*", "GET", "Content-Type", 3600);
        CorsMiddleware middleware = new CorsMiddleware(config);

        AtomicReference<String> result = new AtomicReference<>();
        Handler handler =
                middleware.apply(
                        ctx -> {
                            result.set("delegated");
                            ctx.text(HttpStatus.OK, "response");
                        });

        HttpContext ctx = ctx(HttpMethod.GET, "/api/test");
        handler.handle(ctx);

        assertEquals("delegated", result.get());
        assertEquals("response", new String(ctx.body(), StandardCharsets.UTF_8));
    }

    @Test
    void shouldUseCustomConfig() {
        CorsConfig config = new CorsConfig("https://example.com", "GET", "Authorization", 7200);
        CorsMiddleware middleware = new CorsMiddleware(config);

        Handler handler = middleware.apply(ctx -> {});

        HttpContext ctx = ctx(HttpMethod.GET, "/api/test");
        handler.handle(ctx);

        assertEquals("https://example.com", ctx.headers().get("Access-Control-Allow-Origin"));
        assertEquals("GET", ctx.headers().get("Access-Control-Allow-Methods"));
        assertEquals("Authorization", ctx.headers().get("Access-Control-Allow-Headers"));
        assertEquals("7200", ctx.headers().get("Access-Control-Max-Age"));
    }

    private HttpContext ctx(HttpMethod method, String path) {
        Request req = new Request(method, path, null, null, new byte[0]);
        return new HttpContext(req);
    }
}
