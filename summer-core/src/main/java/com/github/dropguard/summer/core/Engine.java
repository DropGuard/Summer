package com.github.dropguard.summer.core;

/**
 * DI engine selection enum.
 *
 * <p>
 * Passed to {@code SummerApplication.run(Engine, String[])} or read from
 * {@link BeanContainer#engine()} to determine which engine produced a
 * container.
 * </p>
 */
public enum Engine {

	/** Compile-time generated context (requires summer-maven-plugin). */
	AOT,

	/** Runtime DI engine: reads Jandex index at startup. */
	RUNTIME
}
