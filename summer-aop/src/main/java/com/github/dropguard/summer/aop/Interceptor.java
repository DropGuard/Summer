package com.github.dropguard.summer.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as an AOP interceptor.
 *
 * <p>
 * Interceptors are classes that implement {@link MethodInterceptor} and are
 * bound to target methods/classes via {@link InterceptorBinding} annotations.
 * </p>
 *
 * <p>
 * An interceptor class must also carry one or more {@code @InterceptorBinding}
 * annotations to declare which binding(s) it handles.
 * </p>
 *
 * <p>
 * Example:
 * </p>
 *
 * <pre>
 * {@code
 * // 1. Define binding annotation
 * &#64;InterceptorBinding
 * &#64;Target({ElementType.TYPE, ElementType.METHOD})
 * &#64;Retention(RetentionPolicy.RUNTIME)
 * public @interface Transactional { }
 *
 * // 2. Interceptor: @Interceptor + binding annotation
 * &#64;Interceptor
 * &#64;Transactional
 * public class TransactionInterceptor implements MethodInterceptor { ... }
 *
 * // 3. Target: binding annotation on method or class
 * &#64;Transactional
 * public void transferMoney() { ... }
 * }
 * </pre>
 *
 * @see InterceptorBinding
 * @see MethodInterceptor
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Interceptor {
}
