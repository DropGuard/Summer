package summer.example;

import summer.web.Get;
import summer.web.Post;
import summer.web.Put;
import summer.web.Delete;
import summer.web.RestController;
import summer.web.Request;
import summer.core.Component;

import java.util.Map;

@RestController("/users")
public record UserController(UserService userService) {

    @Get("")
    public Map<String, User> getAllUsers(Request req) {
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