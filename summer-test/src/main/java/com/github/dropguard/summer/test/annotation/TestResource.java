package com.github.dropguard.summer.test.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a {@link com.github.dropguard.summer.test.TestResource} whose {@code start()} output is
 * injected as config overrides before the DI container is built. Repeatable per test class.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(TestResource.List.class)
public @interface TestResource {

    Class<? extends com.github.dropguard.summer.test.TestResource> value();

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface List {
        TestResource[] value();
    }
}
