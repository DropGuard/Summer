package com.github.dropguard.summer.fixtures.dummy;

import com.github.dropguard.summer.core.Component;

@Component
public class ServiceA {
    private final ServiceB serviceB;

    public ServiceA(ServiceB serviceB) {
        this.serviceB = serviceB;
    }

    public ServiceB getServiceB() {
        return serviceB;
    }
}
