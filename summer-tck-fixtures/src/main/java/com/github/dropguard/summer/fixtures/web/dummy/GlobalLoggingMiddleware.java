package com.github.dropguard.summer.fixtures.web.dummy;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.web.Handler;
import com.github.dropguard.summer.web.Middleware;
import com.github.dropguard.summer.web.annotation.GlobalMiddleware;
import java.nio.charset.StandardCharsets;

@Component
@GlobalMiddleware
public class GlobalLoggingMiddleware implements Middleware {
    @Override
    public Handler apply(Handler next) {
        return ctx -> {
            next.handle(ctx);
            byte[] body = ctx.body();
            String content = body != null ? new String(body, StandardCharsets.UTF_8) : "";
            ctx.text(ctx.statusCode(), "[global-logged] " + content);
        };
    }
}
