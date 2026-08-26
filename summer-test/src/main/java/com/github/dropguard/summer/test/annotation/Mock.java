package com.github.dropguard.summer.test.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a constructor parameter as a mock bean. The framework creates a {@code Mockito.mock()}
 * instance, registers it in the container, and injects it into the test class.
 *
 * <p>Mocks are registered <em>before</em> the container is built, so the real bean (if any) is
 * skipped — the mock always wins.
 *
 * <p><strong>Contract.</strong> Skipping is by assignable-type closure: every candidate bean whose
 * class, superclasses, or interfaces are assignable to (or from) the mocked type is removed —
 * mocking an interface removes all its implementations, mocking a base class removes subclasses.
 * Beans depending on any removed bean receive the mock instead, resolved through the same
 * dependency-resolution path as production wiring. The mock itself is injectable both as the
 * declared type and as any of its supertypes/interfaces.
 *
 * <pre>{@code
 * &#64;SummerTest({UserService.class})
 * class UserServiceTest {
 * 	UserServiceTest(UserService service, &#64;Mock UserRepository repo) {
 * 		this.service = service;
 * 		this.repo = repo;
 * 	}
 * }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Mock {}
