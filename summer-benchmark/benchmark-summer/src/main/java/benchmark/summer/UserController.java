package com.github.dropguard.summer.benchmark;


import com.github.dropguard.summer.web.annotation.Get;
import com.github.dropguard.summer.web.annotation.PathParam;
import com.github.dropguard.summer.web.annotation.RestController;

@RestController("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Get("/{id}")
    public User getUser(@PathParam("id") String id) {
        return userService.getUser(id);
    }

    @com.github.dropguard.summer.web.annotation.Post
    public User createUser(User user) {
        return userService.createUser(user);
    }

    @com.github.dropguard.summer.web.annotation.Put("/{id}")
    public User updateUser(@PathParam("id") String id, User user) {
        return userService.updateUser(id, user);
    }

    @com.github.dropguard.summer.web.annotation.Delete("/{id}")
    public void deleteUser(@PathParam("id") String id) {
        userService.deleteUser(id);
    }
}
