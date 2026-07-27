package com.github.dropguard.summer.fixtures.di.generic;

import com.github.dropguard.summer.core.Component;

@Component
public class GenericServiceClient {

    private final GenericService<String> service;

    public GenericServiceClient(GenericService<String> service) {
        this.service = service;
    }

    public GenericService<String> getService() {
        return service;
    }
}
