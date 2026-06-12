package summer.fixtures.web.dummy;

import java.nio.charset.StandardCharsets;
import summer.core.Component;
import summer.web.Handler;
import summer.web.Middleware;

@Component
public class MyMiddleware implements Middleware {
	@Override
	public Handler apply(Handler next) {
		return ctx -> {
			next.handle(ctx);
			byte[] body = ctx.body();
			String content = body != null ? new String(body, StandardCharsets.UTF_8) : "";
			ctx.text(ctx.statusCode(), "[secured] " + content);
		};
	}
}
