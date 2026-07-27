package com.github.dropguard.summer.fixtures.aop;

/**
 * The interface that the proxy will implement. JDK dynamic proxy requires an interface --this is a
 * fundamental constraint of Summer's AOP model (no CGLIB subclassing).
 */
public interface Greeter {
    String greet(String name);

    String shout(String message);
}
