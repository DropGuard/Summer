package com.github.dropguard.summer.fixtures.web.dummy;

import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.annotation.Get;
import com.github.dropguard.summer.web.annotation.RestController;

@RestController("/api/class-level")
public class ClassLevelMiddlewareController {

    @Get("/test")
    public String test(HttpContext ctx) {
        return "test";
    }
}
