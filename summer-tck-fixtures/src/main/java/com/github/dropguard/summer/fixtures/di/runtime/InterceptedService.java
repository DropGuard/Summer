package com.github.dropguard.summer.fixtures.di.runtime;

public interface InterceptedService {
    @TestIntercepted
    String interceptedGreet(String name);

    String nonInterceptedGreet(String name);
}
