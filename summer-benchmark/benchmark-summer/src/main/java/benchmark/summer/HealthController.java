package com.github.dropguard.summer.benchmark;

import com.github.dropguard.summer.web.annotation.Get;
import com.github.dropguard.summer.web.annotation.RestController;

@RestController
public class HealthController {

    @Get("/_system/health")
    public String health() {
        return "OK";
    }
}
