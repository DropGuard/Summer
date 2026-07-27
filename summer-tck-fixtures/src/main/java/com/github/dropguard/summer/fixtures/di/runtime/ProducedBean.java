package com.github.dropguard.summer.fixtures.di.runtime;

public class ProducedBean {
    private final String value;

    public ProducedBean(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
