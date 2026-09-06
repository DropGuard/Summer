package com.github.dropguard.summer.fixtures.aop.metadata;

import com.github.dropguard.summer.core.Component;

@Component
public class OverloadTaggedServiceImpl implements OverloadTaggedService {

    @Override
    public String greet(String name) {
        return "hi " + name;
    }

    @Override
    public String greet(int times) {
        return "hi".repeat(times);
    }
}
