package summer.example;

import java.util.List;
import summer.web.Request;
import summer.web.annotation.Delete;
import summer.web.annotation.Get;
import summer.web.annotation.Post;
import summer.web.annotation.Put;
import summer.web.annotation.RestController;

@RestController("/users")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@Get("")
	public List<User> getAllUsers(Request req) {
		return userService.findAll();
	}

	@Get("/{id}")
	public User getUser(Request req) {
		return userService.findById(req.pathParam("id"));
	}

	@Post("")
	public User createUser(Request req) {
		return userService.create(req.body(User.class));
	}

	@Put("/{id}")
	public User updateUser(Request req) {
		return userService.update(req.pathParam("id"), req.body(User.class));
	}

	@Delete("/{id}")
	public void deleteUser(Request req) {
		userService.delete(req.pathParam("id"));
	}
}