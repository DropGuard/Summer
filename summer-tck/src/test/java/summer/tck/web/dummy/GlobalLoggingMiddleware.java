package summer.tck.web.dummy;

import summer.core.Component;
import summer.web.Handler;
import summer.web.annotation.GlobalMiddleware;
import summer.web.Middleware;

@Component
@GlobalMiddleware
public class GlobalLoggingMiddleware implements Middleware {
	@Override
	public Handler apply(Handler next) {
		return ctx -> {
			Object result = next.handle(ctx);
			return "[global-logged] " + result;
		};
	}
}
