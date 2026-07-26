package com.github.dropguard.summer.fixtures.web.dummy;

import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.annotation.Delete;
import com.github.dropguard.summer.web.annotation.Get;
import com.github.dropguard.summer.web.annotation.PathParam;
import com.github.dropguard.summer.web.annotation.Post;
import com.github.dropguard.summer.web.annotation.Put;
import com.github.dropguard.summer.web.annotation.RestController;

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