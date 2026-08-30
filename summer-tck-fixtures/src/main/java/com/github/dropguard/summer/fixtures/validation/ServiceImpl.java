package com.github.dropguard.summer.fixtures.validation;

import com.github.dropguard.summer.core.Component;

/** Test service implementing ServiceInterface. */
@Component
public class ServiceImpl implements ServiceInterface {

    private final String name = "test-service";

    @Override
    public String getName() {
        return name;
    }
}
