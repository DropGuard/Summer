package summer.fixtures.web.dummy;

import summer.web.HttpContext;
import summer.web.HttpStatus;
import summer.web.annotation.Get;
import summer.web.annotation.PathParam;
import summer.web.annotation.RestController;

/**
 * Minimal HTTP fixture used by cross-module discovery tests. Lives in the
 * aot-engine test sources (not in a demo or the shared tck-fixtures module) so
 * framework tests never depend on application code.
 */
@RestController("/api/users")
public class UserController {

	@Get("/{id}")
	public void getUser(HttpContext ctx, @PathParam("id") String id) {
		ctx.text(HttpStatus.OK, "user:" + id);
	}
}
