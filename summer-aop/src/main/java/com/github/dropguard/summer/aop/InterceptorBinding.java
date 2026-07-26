package com.github.dropguard.summer.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Meta-annotation that marks an annotation as an interceptor binding.
 *
 * <p>
 * Annotations annotated with {@code @InterceptorBinding} serve as both business
 * annotations and interceptor bindings. When a class or method is annotated
 * with an {@code @InterceptorBinding} annotation, the framework automatically
 * applies interceptors that are also annotated with the same binding
 * annotation.
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
 * // 2. Interceptor uses the binding annotation
 * &#64;Transactional
 * public class TransactionInterceptor implements MethodInterceptor { ... }
 *
 * // 3. Business method uses the same annotation
 * &#64;Transactional
 * public void transferMoney() { ... }
 * }
 * </pre>
 *
 * @see MethodInterceptor
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface InterceptorBinding {
}
