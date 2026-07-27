package com.github.dropguard.summer.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.dropguard.summer.web.Handler;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpMethod;
import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.Middleware;
import com.github.dropguard.summer.web.Request;
import com.github.dropguard.summer.web.http.RadixTreeHttpRouter;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MiddlewareTest {

    @Test
    void middlewareWrapsResult() {
        RadixTreeHttpRouter router = new RadixTreeHttpRouter();
        Middleware wrapMiddleware =
                next ->
                        ctx -> {
                            next.handle(ctx);
                            byte[] body = ctx.body();
                            String content =
                                    body != null ? new String(body, StandardCharsets.UTF_8) : "";
                            ctx.text(ctx.statusCode(), "[wrapped] " + content);
                        };

        router.register(
                HttpMethod.GET,
                "/test",
                ctx -> {
                    Handler original = ctx2 -> ctx2.text(HttpStatus.OK, "data");
                    Handler wrapped = wrapMiddleware.apply(original);
                    wrapped.handle(ctx);
                });

        Request req = new Request(HttpMethod.GET, "/test", null, null, null);
        HttpContext ctx = new HttpContext(req);
        router.route(ctx);
        assertEquals("[wrapped] data", new String(ctx.body(), StandardCharsets.UTF_8));
    }

    @Test
    void multipleMiddlewaresChain() {
        Middleware first =
                next ->
                        ctx -> {
                            next.handle(ctx);
                            byte[] body = ctx.body();
                            String content =
                                    body != null ? new String(body, StandardCharsets.UTF_8) : "";
                            ctx.text(ctx.statusCode(), "[1]" + content);
                        };
        Middleware second =
                next ->
                        ctx -> {
                            next.handle(ctx);
                            byte[] body = ctx.body();
                            String content =
                                    body != null ? new String(body, StandardCharsets.UTF_8) : "";
                            ctx.text(ctx.statusCode(), "[2]" + content);
                        };

        Handler original = ctx -> ctx.text(HttpStatus.OK, "core");
        Handler chain = first.apply(second.apply(original));

        Request req = new Request(HttpMethod.GET, "/test", null, null, null);
        HttpContext ctx = new HttpContext(req);
        chain.handle(ctx);
        assertEquals("[1][2]core", new String(ctx.body(), StandardCharsets.UTF_8));
    }
}
