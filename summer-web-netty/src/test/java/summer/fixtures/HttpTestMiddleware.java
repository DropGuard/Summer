package summer.fixtures;

import summer.web.Handler;
import summer.web.Middleware;
import summer.core.Component;

@Component
public class HttpTestMiddleware implements Middleware {
	@Override
	public Handler apply(Handler next) {
		return ctx -> {
			next.handle(ctx);
			ctx.setHeader("X-Test-Middleware", "Active");
		};
	}
}
