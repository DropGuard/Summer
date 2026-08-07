package com.example;

import com.github.dropguard.summer.core.Component;

@Component
public class GreetingService {

    public String greet() {
        return "hello from aot-it";
    }
}
