package com.github.dropguard.summer.benchmark;

import com.github.dropguard.summer.web.annotation.Get;
import com.github.dropguard.summer.web.annotation.Post;
import com.github.dropguard.summer.web.annotation.Put;
import com.github.dropguard.summer.web.annotation.Delete;
import com.github.dropguard.summer.web.annotation.RestController;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpStatus;

@RestController("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Get("/:id")
    public void getUser(HttpContext ctx) {
        String id = ctx.pathParam("id");
        User user = userService.getUser(id);
        if (user == null) {
            ctx.status(HttpStatus.NOT_FOUND);
        } else {
            ctx.json(HttpStatus.OK, user);
        }
    }

    @Post
    public void createUser(HttpContext ctx) {
        User user = ctx.body(User.class);
        User created = userService.createUser(user);
        ctx.json(HttpStatus.OK, created);
    }

    @Put("/:id")
    public void updateUser(HttpContext ctx) {
        String id = ctx.pathParam("id");
        User user = ctx.body(User.class);
        User updated = userService.updateUser(id, user);
        if (updated == null) {
            ctx.status(HttpStatus.NOT_FOUND);
        } else {
            ctx.json(HttpStatus.OK, updated);
        }
    }

    @Delete("/:id")
    public void deleteUser(HttpContext ctx) {
        String id = ctx.pathParam("id");
        userService.deleteUser(id);
        ctx.status(HttpStatus.OK);
    }
}
