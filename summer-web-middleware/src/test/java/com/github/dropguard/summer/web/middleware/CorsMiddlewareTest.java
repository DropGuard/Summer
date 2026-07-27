package com.github.dropguard.summer.web.middleware;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.core.config.ConfigBinder;
import com.github.dropguard.summer.core.config.ConfigBinder.BindingContext;
import com.github.dropguard.summer.runtime.ConfigMappingProxyBinder;
import com.github.dropguard.summer.web.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Tests for {@link CorsMiddleware}. */
class CorsMiddlewareTest {

    @BeforeAll
    static void installInterfaceBinder() {
        // Interface config binding (@ConfigMapping) is provided by the runtime module.
        ConfigMappingProxyBinder.install();
    }

    private static CorsConfig corsProps(
            String allowedOrigins, String allowedMethods, String allowedHeaders, int maxAge) {
        return ConfigBinder.bind(
                BindingContext.of(
                        Map.of(
                                "cors.allowedOrigins", allowedOrigins,
                                "cors.allowedMethods", allowedMethods,
                                "cors.allowedHeaders", allowedHeaders,
                                "cors.maxAge", maxAge)),
                "cors",
                CorsConfig.class);
    }

    @Test
    void shouldSetCorsHeaders() {
        CorsConfig config = corsProps("*", "GET, POST", "Content-Type", 3600);
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
        CorsConfig config = corsProps("*", "GET, POST, PUT, DELETE", "Content-Type", 3600);
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
        CorsConfig config = corsProps("*", "GET", "Content-Type", 3600);
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
    void shouldReflectRequestOriginForNamedAllowList() {
        CorsConfig config = corsProps("https://example.com", "GET", "Authorization", 7200);
        CorsMiddleware middleware = new CorsMiddleware(config);

        Handler handler = middleware.apply(ctx -> {});

        HttpContext ctx =
                CorsContext.builder(HttpMethod.GET, "/api/test")
                        .withOrigin("https://example.com")
                        .withHost("localhost:8080")
                        .build();
        handler.handle(ctx);

        // The matched origin (the requesting one) is reflected, not the raw configured list.
        assertEquals("https://example.com", ctx.headers().get("Access-Control-Allow-Origin"));
        assertEquals("GET", ctx.headers().get("Access-Control-Allow-Methods"));
        assertEquals("Authorization", ctx.headers().get("Access-Control-Allow-Headers"));
        assertEquals("7200", ctx.headers().get("Access-Control-Max-Age"));
    }

    @Test
    void shouldOmitAllowOriginWhenRequestOriginNotAllowed() {
        CorsConfig config = corsProps("https://example.com", "GET", "Authorization", 7200);
        CorsMiddleware middleware = new CorsMiddleware(config);

        Handler handler = middleware.apply(ctx -> {});

        HttpContext ctx =
                CorsContext.builder(HttpMethod.GET, "/api/test")
                        .withOrigin("https://evil.com")
                        .withHost("localhost:8080")
                        .build();
        handler.handle(ctx);

        assertNull(
                ctx.headers().get("Access-Control-Allow-Origin"),
                "Disallowed origin must not be reflected");
    }

    private HttpContext ctx(HttpMethod method, String path) {
        return CorsContext.builder(method, path).build();
    }

    /**
     * Fluent builder for test {@link HttpContext}s — no null placeholders; origin/host are opt-in.
     */
    private static final class CorsContext {
        private final HttpMethod method;
        private final String path;
        private String origin;
        private String host;

        private CorsContext(HttpMethod method, String path) {
            this.method = method;
            this.path = path;
        }

        static CorsContext builder(HttpMethod method, String path) {
            return new CorsContext(method, path);
        }

        CorsContext withOrigin(String origin) {
            this.origin = origin;
            return this;
        }

        CorsContext withHost(String host) {
            this.host = host;
            return this;
        }

        HttpContext build() {
            Map<String, String> headers = new HashMap<>();
            if (origin != null) {
                headers.put("origin", origin);
            }
            if (host != null) {
                headers.put("host", host);
            }
            Request req =
                    new Request(
                            method,
                            path,
                            null,
                            null,
                            new byte[0],
                            headers,
                            path.getBytes(StandardCharsets.UTF_8));
            return new HttpContext(req);
        }
    }
}
