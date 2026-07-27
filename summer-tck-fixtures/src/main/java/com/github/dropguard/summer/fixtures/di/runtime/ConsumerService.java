package com.github.dropguard.summer.fixtures.di.runtime;

import com.github.dropguard.summer.core.Component;

@Component
public class ConsumerService {
    private final SimpleService dependency;

    public ConsumerService(SimpleService dependency) {
        this.dependency = dependency;
    }

    public SimpleService getDependency() {
        return dependency;
    }
}
