package summer.example;

import java.util.List;
import summer.web.WebContext;
import summer.web.annotation.Delete;
import summer.web.annotation.Get;
import summer.web.annotation.PathParam;
import summer.web.annotation.Post;
import summer.web.annotation.Put;
import summer.web.annotation.RestController;
import summer.web.annotation.Use;

@RestController("/users")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@Get("")
	public List<User> getAllUsers() {
		return userService.findAll();
	}

	@Get("/{id}")
	public User getUser(@PathParam("id") String id) {
		return userService.findById(id);
	}

	@Post("")
	public User createUser(User user) {
		return userService.create(user);
	}

	@Put("/{id}")
	public User updateUser(@PathParam("id") String id, User user) {
		return userService.update(id, user);
	}

	@Delete("/{id}")
	@Use(AuthMiddleware.class) // Protect sensitive action
	public void deleteUser(@PathParam("id") String id) {
		userService.delete(id);
	}
}