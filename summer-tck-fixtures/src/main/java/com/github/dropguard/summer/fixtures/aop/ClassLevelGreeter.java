package com.github.dropguard.summer.fixtures.aop;

/**
 * Interface for {@link ClassLevelService}. Separated from {@link Greeter} to avoid ambiguous bean
 * lookup when both services are in the same context.
 */
public interface ClassLevelGreeter {
    String greet(String name);

    String shout(String message);
}
