package summer.example;

import summer.core.Component;
import summer.web.Handler;
import summer.web.HttpStatus;
import summer.web.Middleware;

@Component
public class AuthMiddleware implements Middleware {
	@Override
	public Handler apply(Handler next) {
		return ctx -> {
			String auth = ctx.header("Authorization");
			if (auth == null || !auth.equals("secret-token")) {
				ctx.json(HttpStatus.UNAUTHORIZED, new ErrorResponse("Unauthorized", "Invalid or missing token"));
				return;
			}
			next.handle(ctx);
		};
	}

	public record ErrorResponse(String error, String message) {
	}
}
