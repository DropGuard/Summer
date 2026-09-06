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
 *
 * <p>Deliberately attribute-free (no {@code rollbackFor}/{@code readOnly}/{@code propagation}): the
 * transaction model is intentionally minimal — REQUIRED-only semantics, and <em>any</em> thrown
 * exception (checked or unchecked) rolls back. Callers needing selective rollback should handle the
 * exception themselves or not use transactions.
 *
 * <p><strong>Nested transactions are intentionally unsupported.</strong> A {@code @Transactional}
 * method invoked through the proxy from inside another active transaction fails loudly — the nested
 * {@code begin()} throws {@code SummerTransactionException} and the outer boundary rolls back.
 * Compose transactional work at one boundary per thread (the service layer) rather than nesting
 * across beans. Same-bean internal calls ({@code this.method()}) bypass the proxy entirely and
 * never open a nested transaction (see README "Interface-based AOP").
 *
 * <p><strong>The transaction context does not cross threads — by design.</strong> The boundary is
 * bound via {@code ScopedValue} on the calling thread (the request's virtual thread), and work
 * dispatched to ANOTHER thread — a spawned virtual thread, an executor, a parallel stream — runs
 * OUTSIDE the transaction: it gets its own auto-commit connection, and its writes survive a
 * rollback of the outer boundary. Nothing propagates and nothing is detected; thread confinement is
 * the model, not a gap to be papered over. Keep transactional data access on the one thread, and
 * compose concurrent work outside the boundary or with explicit coordination.
 */
@InterceptorBinding
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Transactional {}
