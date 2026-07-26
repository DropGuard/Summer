package com.github.dropguard.summer.fixtures;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.web.Handler;
import com.github.dropguard.summer.web.Middleware;
import com.github.dropguard.summer.web.annotation.GlobalMiddleware;

@Component
@GlobalMiddleware
public class HttpTestMiddleware implements Middleware {
	@Override
	public Handler apply(Handler next) {
		return ctx -> {
			next.handle(ctx);
			ctx.setHeader("X-Test-Middleware", "Active");
		};
	}
}
