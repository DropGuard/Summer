package summer.fixtures.web.dummy;

import summer.web.HttpContext;
import summer.web.HttpStatus;
import summer.web.annotation.Delete;
import summer.web.annotation.Get;
import summer.web.annotation.PathParam;
import summer.web.annotation.Post;
import summer.web.annotation.Put;
import summer.web.annotation.RestController;

@RestController("/api/users")
public class UserController {

	@Get("/{id}")
	public void getUser(HttpContext ctx, @PathParam("id") String id) {
		if ("error".equals(id)) {
			throw new IllegalArgumentException("invalid id");
		}
		ctx.text(HttpStatus.OK, "user:" + id);
	}

	@Post("")
	public void createUser(HttpContext ctx, UserDto body) {
		ctx.text(HttpStatus.CREATED, "created:" + body.name());
	}

	@Put("/{id}")
	public void updateUser(HttpContext ctx, @PathParam("id") String id, UserDto body) {
		ctx.text(HttpStatus.OK, "updated:" + id + ":" + body.name());
	}

	@Delete("/{id}")
	public void deleteUser(HttpContext ctx, @PathParam("id") String id) {
		ctx.text(HttpStatus.OK, "deleted:" + id);
	}

	@Get("/secured")
	public void getSecured(HttpContext ctx) {
		ctx.text(HttpStatus.OK, "secret");
	}

	@Get("/multi")
	public void multiMiddleware(HttpContext ctx) {
		ctx.text(HttpStatus.OK, "multi");
	}
}