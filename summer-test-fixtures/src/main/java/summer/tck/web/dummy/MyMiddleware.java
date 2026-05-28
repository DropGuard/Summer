package summer.tck.web.dummy;

import summer.core.Component;
import summer.web.Handler;
import summer.web.middleware.Middleware;

@Component
public class MyMiddleware implements Middleware {
	@Override
	public Handler apply(Handler next) {
		return ctx -> {
			Object res = next.handle(ctx);
			return "[secured] " + res;
		};
	}
}
