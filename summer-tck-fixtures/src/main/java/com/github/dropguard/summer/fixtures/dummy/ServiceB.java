package com.github.dropguard.summer.fixtures.dummy;

import com.github.dropguard.summer.core.Component;

@Component
public class ServiceB {
    private final ServiceC serviceC;

    public ServiceB(ServiceC serviceC) {
        this.serviceC = serviceC;
    }

    public ServiceC getServiceC() {
        return serviceC;
    }
}
