package summer.fixtures.dummy;

import summer.web.HttpContext;
import summer.web.annotation.Delete;
import summer.web.annotation.Get;
import summer.web.annotation.PathParam;
import summer.web.annotation.Put;
import summer.web.annotation.RestController;

@RestController("/dummy")
public class DummyController {

    @Get("/{id}")
    public void getById(HttpContext ctx, @PathParam("id") String id) {
    }

    @Put("/{id}")
    public void updateById(HttpContext ctx, @PathParam("id") String id, String body) {
    }

    @Delete("/{id}")
    public void deleteById(HttpContext ctx, @PathParam("id") String id) {
    }
}