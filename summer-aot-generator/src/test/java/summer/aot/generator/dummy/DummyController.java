package summer.aot.generator.dummy;
import summer.web.annotation.RestController;
import summer.web.annotation.Get;
import summer.web.annotation.PathParam;
@RestController
public class DummyController {
    @Get("/dummy/{id}")
    public String getById(@PathParam("id") String id) { return id; }
}
