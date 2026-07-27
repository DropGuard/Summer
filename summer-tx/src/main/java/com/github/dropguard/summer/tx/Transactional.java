package com.github.dropguard.summer.tx;

import com.github.dropguard.summer.aop.InterceptorBinding;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as transactional. Only methods in classes that implement interfaces will be
 * intercepted using JDK dynamic proxies.
 *
 * <p>This annotation also serves as an interceptor binding. The {@code TransactionInterceptor} is
 * automatically applied to methods annotated with {@code @Transactional}.
 */
@InterceptorBinding
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Transactional {}
