package com.github.dropguard.summer.fixtures.di.runtime;

import com.github.dropguard.summer.core.Component;

@Component
public class SimpleService {
    public String doWork() {
        return "done";
    }
}
