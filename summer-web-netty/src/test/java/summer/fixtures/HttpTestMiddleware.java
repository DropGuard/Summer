package summer.fixtures;

import summer.web.Handler;
import summer.web.Middleware;
import summer.web.annotation.GlobalMiddleware;

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
