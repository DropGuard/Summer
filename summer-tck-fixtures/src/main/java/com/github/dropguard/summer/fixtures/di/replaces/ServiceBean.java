package com.github.dropguard.summer.fixtures.di.replaces;

public class ServiceBean {

    private final String source;

    public ServiceBean(String source) {
        this.source = source;
    }

    public String source() {
        return source;
    }
}
