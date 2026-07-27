package com.github.dropguard.summer.core;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a type, method, or field as part of Summer's <b>internal</b> API.
 *
 * <p>Internal API is not part of the public, user-facing contract. It may change or be removed
 * without notice, and framework consumers should not depend on it. The compiler does not enforce
 * this boundary (Java has no module-private modifier); the annotation is a signal, reinforced by
 * instability — depending on an internal type means a framework upgrade can break your code.
 *
 * <p>Public API for framework users is limited to the {@code @SummerTest}, {@code @Mock}, and
 * {@code @TestProfile} annotations plus the {@code Testing} facade ({@code build()} / {@code
 * buildForTest(Class)}). Everything else in {@code summer-test} is internal unless explicitly
 * documented otherwise.
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD})
public @interface Internal {}
