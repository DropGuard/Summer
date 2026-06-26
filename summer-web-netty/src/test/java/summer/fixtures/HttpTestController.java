package summer.fixtures;

import summer.web.HttpContext;
import summer.web.HttpStatus;
import summer.web.annotation.Get;
import summer.web.annotation.RestController;

@RestController("/test")
public class HttpTestController {
	@Get("/hello")
	public void hello(HttpContext ctx) {
		ctx.text(HttpStatus.OK, "world");
	}
}
