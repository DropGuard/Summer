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
