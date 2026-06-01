package summer.example;

import jakarta.validation.Valid;
import summer.web.HttpStatus;
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
	public void getAllUsers(WebContext ctx) {
		ctx.json(HttpStatus.OK, userService.findAll());
	}

	@Get("/{id}")
	public void getUser(WebContext ctx, @PathParam("id") String id) {
		ctx.json(HttpStatus.OK, userService.findById(id));
	}

	@Post("")
	public void createUser(WebContext ctx, @Valid UserDto userDto) {
		User user = new User(null, userDto.name(), userDto.email());
		ctx.json(HttpStatus.CREATED, userService.create(user));
	}

	@Put("/{id}")
	public void updateUser(WebContext ctx, @PathParam("id") String id, @Valid UserDto userDto) {
		User user = new User(id, userDto.name(), userDto.email());
		ctx.json(HttpStatus.OK, userService.update(id, user));
	}

	@Delete("/{id}")
	@Use(AuthMiddleware.class) // Protect sensitive action
	public void deleteUser(WebContext ctx, @PathParam("id") String id) {
		userService.delete(id);
		ctx.status(HttpStatus.NO_CONTENT);
	}
}