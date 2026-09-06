package com.github.dropguard.summer.fixtures.aop.metadata;

/**
 * Overload-exact binding contract: two same-named methods, only one carrying the binding.
 * Pre-signature-keying, the binding map was keyed by method NAME alone, so both overloads shared
 * whichever declaration discovery saw — {@code greet(String)} annotated meant {@code greet(int)}
 * intercepted too.
 */
public interface OverloadTaggedService {

    @MetadataTagged
    String greet(String name);

    String greet(int times);
}
