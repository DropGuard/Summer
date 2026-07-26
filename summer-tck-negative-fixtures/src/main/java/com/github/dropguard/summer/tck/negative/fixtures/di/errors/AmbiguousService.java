package com.github.dropguard.summer.tck.negative.fixtures.di.errors;

/**
 * Interface with two concrete {@code @Component} implementations and no
 * disambiguation ({@code @Primary} / qualifier). Resolving by type must fail
 * with {@code AmbiguousBeanException}.
 */
public interface AmbiguousService {
	String name();
}
