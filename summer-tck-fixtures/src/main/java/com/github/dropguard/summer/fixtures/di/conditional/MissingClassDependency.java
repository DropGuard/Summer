package com.github.dropguard.summer.fixtures.di.conditional;

/**
 * A prerequisite that is never registered as a bean (no implementation, no component). Used to
 * prove AND semantics: a class-level condition on this type must fail even when the producer
 * method's own condition passes.
 */
public interface MissingClassDependency {}
