package com.github.dropguard.summer.fixtures.di.runtime;

import com.github.dropguard.summer.core.Component;

@Component
public class Dog implements Animal {
    @Override
    public String sound() {
        return "woof";
    }
}
