package com.github.dropguard.summer.fixtures;

import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.annotation.Get;
import com.github.dropguard.summer.web.annotation.RestController;

@RestController("/test")
public class HttpTestController {
    @Get("/hello")
    public void hello(HttpContext ctx) {
        ctx.text(HttpStatus.OK, "world");
    }
}
