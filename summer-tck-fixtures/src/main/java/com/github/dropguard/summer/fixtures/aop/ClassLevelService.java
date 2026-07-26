package com.github.dropguard.summer.fixtures.aop;

import com.github.dropguard.summer.core.Component;

/**
 * Test fixture: a service with {@code @Logged} at the CLASS level.
 *
 * <p>
 * Unlike {@link GreeterService} where only {@code greet()} is annotated, here
 * the entire class is annotated. This verifies that the AOP engine detects
 * class-level interceptor bindings and proxies ALL methods.
 * </p>
 */
@Component
@Logged
public class ClassLevelService implements ClassLevelGreeter {

	@Override
	public String greet(String name) {
		return "Hello, " + name;
	}

	@Override
	public String shout(String message) {
		return message.toUpperCase();
	}
}
