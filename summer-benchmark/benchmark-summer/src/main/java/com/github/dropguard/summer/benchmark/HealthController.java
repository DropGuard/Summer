package com.github.dropguard.summer.benchmark;

import com.github.dropguard.summer.web.annotation.Get;
import com.github.dropguard.summer.web.annotation.RestController;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpStatus;

@RestController
public class HealthController {

    @Get("/_system/health")
    public void health(HttpContext ctx) {
        ctx.text(HttpStatus.OK, "OK");
    }
}
