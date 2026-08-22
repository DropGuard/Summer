package com.github.dropguard.summer.web.jsonb;

import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.annotation.Get;
import com.github.dropguard.summer.web.annotation.Post;
import com.github.dropguard.summer.web.annotation.RestController;

@RestController
public class PersonController {

    @Get("/person")
    public void getPerson(HttpContext ctx) {
        ctx.ok(new PersonDto("Charlie", 28, "charlie@example.com"));
    }

    @Post("/person")
    public void createPerson(HttpContext ctx) {
        PersonDto person = ctx.body(PersonDto.class);
        ctx.ok(person);
    }
}
