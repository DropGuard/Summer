package com.example.baseline;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/_system/health")
    public String health() {
        return "OK";
    }
}
