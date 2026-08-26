package com.github.dropguard.summer.fixtures.aop.metadata;

import com.github.dropguard.summer.aop.InterceptorBinding;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binding annotation for verifying that class-level bindings reach {@code InterceptedMethod}.
 *
 * <p>Deliberately separate from {@link com.github.dropguard.summer.fixtures.aop.Logged} so this
 * fixture's interceptor does not attach to the {@code Logged}-bound beans used by the broader AOP
 * behavior tests (those assert exact call logs).
 */
@InterceptorBinding
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ClassMetadataTagged {}
