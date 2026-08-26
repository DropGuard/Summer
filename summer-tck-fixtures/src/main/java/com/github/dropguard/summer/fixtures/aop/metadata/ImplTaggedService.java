package com.github.dropguard.summer.fixtures.aop.metadata;

/**
 * Row "method-level on the implementation class": {@code @ClassMetadataTagged} sits on the
 * overriding method, the interface is clean. Pins that impl-declared method bindings are discovered
 * (Runtime scans impl methods when no class-level binding exists) AND materialised into
 * InterceptedMethod (the AOT generator must not rely on interface annotations alone).
 */
public interface ImplTaggedService {

    String op(String input);
}
