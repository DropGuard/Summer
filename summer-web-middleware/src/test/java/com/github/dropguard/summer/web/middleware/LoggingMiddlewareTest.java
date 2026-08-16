package com.github.dropguard.summer.web.middleware;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.web.Handler;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpMethod;
import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.Request;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/** Tests for {@link LoggingMiddleware}. */
class LoggingMiddlewareTest {

    @Test
    void shouldCallNextHandler() throws Exception {
        LoggingMiddleware middleware = new LoggingMiddleware();

        AtomicBoolean called = new AtomicBoolean(false);
        Handler handler =
                middleware.apply(
                        ctx -> {
                            called.set(true);
                            ctx.status(HttpStatus.OK);
                        });

        Request request = new Request(HttpMethod.GET, "/test", null, null, new byte[0]);
        HttpContext ctx = new HttpContext(request);
        handler.handle(ctx);

        assertTrue(called.get());
    }

    @Test
    void shouldPropagateExceptionFromNextHandler() throws Exception {
        LoggingMiddleware middleware = new LoggingMiddleware();

        Handler handler =
                middleware.apply(
                        ctx -> {
                            throw new RuntimeException("handler failure");
                        });

        Request request = new Request(HttpMethod.GET, "/test", null, null, new byte[0]);
        HttpContext ctx = new HttpContext(request);

        assertThrows(RuntimeException.class, () -> handler.handle(ctx));
    }
}
