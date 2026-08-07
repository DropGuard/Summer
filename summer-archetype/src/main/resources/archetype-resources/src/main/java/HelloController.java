package ${package};

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.web.annotation.Get;
import com.github.dropguard.summer.web.annotation.RestController;

/** Minimal endpoint to show the annotation contract. */
@RestController
@Component
public class HelloController {

    @Get("/hello")
    public String hello() {
        return "hello from Summer";
    }
}
