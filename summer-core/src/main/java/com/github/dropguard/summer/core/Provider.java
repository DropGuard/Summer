package com.github.dropguard.summer.core;

/**
 * A marker interface for providing instances of third-party classes or complex
 * objects to the Summer IoC container without requiring @Bean reflection magic.
 * 
 * The IoC container will automatically invoke `provide()` and register the
 * result as a singleton of type T.
 *
 * @param <T>
 *            The type of the object this provider creates.
 */
public interface Provider<T> {

	/**
	 * Creates and configures the instance to be managed by the IoC container.
	 */
	T provide();
}
