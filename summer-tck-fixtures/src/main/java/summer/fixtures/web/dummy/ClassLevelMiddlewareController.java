package summer.fixtures.web.dummy;

import summer.web.annotation.Get;
import summer.web.annotation.RestController;

@RestController("/api/class-level")
public class ClassLevelMiddlewareController {

	@Get("/test")
	public String test() {
		return "test";
	}
}
