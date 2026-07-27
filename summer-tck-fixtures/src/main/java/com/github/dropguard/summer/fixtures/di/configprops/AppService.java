package com.github.dropguard.summer.fixtures.di.configprops;

/** Test fixture: service that receives auto-bound properties via constructor. */
public class AppService {

    private final AppProperties properties;

    public AppService(AppProperties properties) {
        this.properties = properties;
    }

    public AppProperties getProperties() {
        return properties;
    }
}
