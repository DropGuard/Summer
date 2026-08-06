package com.github.dropguard.summer.fixtures.web.dummy;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.web.Handler;
import com.github.dropguard.summer.web.Middleware;
import java.nio.charset.StandardCharsets;

@Component
public class LoggingMiddleware implements Middleware {
    @Override
    public Handler apply(Handler next) {
        return ctx -> {
            next.handle(ctx);
            byte[] body = ctx.body();
            String content = body != null ? new String(body, StandardCharsets.UTF_8) : "";
            ctx.text(ctx.status(), "[class-logged] " + content);
        };
    }
}
