package summer.tck.web;

import summer.web.HttpContext;
import summer.web.HttpStatus;
import summer.web.annotation.Delete;
import summer.web.annotation.ExceptionHandler;
import summer.web.annotation.Get;
import summer.web.annotation.PathParam;
import summer.web.annotation.Post;
import summer.web.annotation.Put;
import summer.web.annotation.RestController;

/**
 * TCK-level routing fixture.
 *
 * <p>
 * Deliberately mechanism-focused and business-agnostic: it exists only so the
 * web routing TCK ({@code AbstractWebRouteTCK}) can assert dispatch, path-param
 * binding, request-body parsing and exception propagation without depending on
 * any application's controllers. The route table here is the contract that
 * {@code routeTestCases()} asserts against — keep the two in sync.
 * </p>
 */
@RestController("/rt")
public class RoutingFixtureController {

	public record NameBody(String name) {
	}

	@Get("/users/{id}")
	public void getUser(@PathParam("id") String id, HttpContext ctx) {
		ctx.text(HttpStatus.OK, "user:" + id);
	}

	@Post("/users")
	public void createUser(HttpContext ctx) {
		NameBody body = ctx.body(NameBody.class);
		ctx.text(HttpStatus.OK, "created:" + body.name());
	}

	@Put("/users/{id}")
	public void updateUser(@PathParam("id") String id, HttpContext ctx) {
		NameBody body = ctx.body(NameBody.class);
		ctx.text(HttpStatus.OK, "updated:" + id + ":" + body.name());
	}

	@Delete("/users/{id}")
	public void deleteUser(@PathParam("id") String id, HttpContext ctx) {
		ctx.text(HttpStatus.OK, "deleted:" + id);
	}

	@Get("/secured")
	public void secured(HttpContext ctx) {
		ctx.text(HttpStatus.OK, "secret");
	}

	@Get("/error")
	public void error(HttpContext ctx) {
		throw new IllegalArgumentException("invalid id");
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public void onIllegalArgument(IllegalArgumentException ex, HttpContext ctx) {
		ctx.text(HttpStatus.BAD_REQUEST, "error_caught:" + ex.getMessage());
	}
}
