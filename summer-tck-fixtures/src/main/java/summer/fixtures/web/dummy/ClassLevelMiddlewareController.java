package summer.fixtures.web.dummy;

import summer.web.HttpContext;
import summer.web.annotation.Get;
import summer.web.annotation.RestController;

@RestController("/api/class-level")
public class ClassLevelMiddlewareController {

    @Get("/test")
    public String test(HttpContext ctx) {
        return "test";
    }
}