package summer.tck.web.dummy;

import summer.core.Component;
import summer.web.Handler;
import summer.web.Middleware;

@Component
public class LoggingMiddleware implements Middleware {
	@Override
	public Handler apply(Handler next) {
		return ctx -> {
			Object res = next.handle(ctx);
			return "[class-logged] " + res;
		};
	}
}
