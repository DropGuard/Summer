package summer.example;

import jakarta.validation.Valid;
import java.util.List;
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
	public User createUser(@Valid UserDto userDto) {
		User user = new User(null, userDto.name(), userDto.email());
		return userService.create(user);
	}

	@Put("/{id}")
	public User updateUser(@PathParam("id") String id, @Valid UserDto userDto) {
		User user = new User(id, userDto.name(), userDto.email());
		return userService.update(id, user);
	}

	@Delete("/{id}")
	@Use(AuthMiddleware.class) // Protect sensitive action
	public void deleteUser(@PathParam("id") String id) {
		userService.delete(id);
	}
}