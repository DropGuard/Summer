package com.example.baseline;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;



@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}")
    public User getUser(@PathVariable("id") String id) {
        return userService.getUser(id);
    }

    @org.springframework.web.bind.annotation.PostMapping
    public User createUser(@org.springframework.web.bind.annotation.RequestBody User user) {
        return userService.createUser(user);
    }

    @org.springframework.web.bind.annotation.PutMapping("/{id}")
    public User updateUser(@PathVariable("id") String id, @org.springframework.web.bind.annotation.RequestBody User user) {
        return userService.updateUser(id, user);
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{id}")
    public void deleteUser(@PathVariable("id") String id) {
        userService.deleteUser(id);
    }
}
