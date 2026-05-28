package benchmark.summer;

import summer.web.annotation.Get;
import summer.web.annotation.RestController;

@RestController
public class HealthController {

    @Get("/_system/health")
    public String health() {
        return "OK";
    }
}
