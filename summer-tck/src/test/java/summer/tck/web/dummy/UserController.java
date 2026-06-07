package summer.tck.web.dummy;

import summer.web.annotation.Delete;
import summer.web.annotation.Get;
import summer.web.annotation.PathParam;
import summer.web.annotation.Post;
import summer.web.annotation.Put;
import summer.web.annotation.RestController;

@RestController("/api/users")
public class UserController {

	@Get("/{id}")
	public String getUser(@PathParam("id") String id) {
		if ("error".equals(id)) {
			throw new IllegalArgumentException("invalid id");
		}
		return "user:" + id;
	}

	@Post("")
	public String createUser(UserDto body) {
		return "created:" + body.name();
	}

	@Put("/{id}")
	public String updateUser(@PathParam("id") String id, UserDto body) {
		return "updated:" + id + ":" + body.name();
	}

	@Delete("/{id}")
	public String deleteUser(@PathParam("id") String id) {
		return "deleted:" + id;
	}

	@Get("/secured")
	public String getSecured() {
		return "secret";
	}

	@Get("/multi")
	public String multiMiddleware() {
		return "multi";
	}
}
