package com.github.dropguard.summer.fixtures.dummy;

import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.annotation.Delete;
import com.github.dropguard.summer.web.annotation.Get;
import com.github.dropguard.summer.web.annotation.PathParam;
import com.github.dropguard.summer.web.annotation.Put;
import com.github.dropguard.summer.web.annotation.RestController;

@RestController("/dummy")
public class DummyController {

    @Get("/{id}")
    public void getById(HttpContext ctx, @PathParam("id") String id) {}

    @Put("/{id}")
    public void updateById(HttpContext ctx, @PathParam("id") String id, String body) {}

    @Delete("/{id}")
    public void deleteById(HttpContext ctx, @PathParam("id") String id) {}
}
