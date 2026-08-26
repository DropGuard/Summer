package com.github.dropguard.summer.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Meta-annotation that marks an annotation as an interceptor binding.
 *
 * <p>Annotations annotated with {@code @InterceptorBinding} serve as both business annotations and
 * interceptor bindings. When a class or method is annotated with an {@code @InterceptorBinding}
 * annotation, the framework automatically applies interceptors that are also annotated with the
 * same binding annotation.
 *
 * <p><strong>Resolution contract (union).</strong> A method's binding set is the UNION of the
 * class-level bindings declared on the bean (implementation class or interface) and any
 * method-level bindings declared on that method — never a replacement of one level by the other.
 * Both DI engines materialise this identical set into {@link InterceptedMethod}. This follows the
 * CDI interceptor-binding resolution convention (Jakarta CDI, "Interceptor bindings": a business
 * method's bindings include those declared at class level together with all bindings declared at
 * method level). Note this concerns binding TYPE visibility only; annotation member values are out
 * of scope here — {@code InterceptedMethod} deliberately exposes presence, not members.
 *
 * <p>Example:
 *
 * <pre>{@code
 * // 1. Define binding annotation
 * @InterceptorBinding
 * @Target({ElementType.TYPE, ElementType.METHOD})
 * @Retention(RetentionPolicy.RUNTIME)
 * public @interface Transactional { }
 *
 * // 2. Interceptor uses the binding annotation
 * @Transactional
 * public class TransactionInterceptor implements MethodInterceptor { ... }
 *
 * // 3. Business method uses the same annotation
 * @Transactional
 * public void transferMoney() { ... }
 * }</pre>
 *
 * @see MethodInterceptor
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface InterceptorBinding {}
